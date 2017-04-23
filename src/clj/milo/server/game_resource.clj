(ns milo.server.game-resource
  (:require [aleph.http :as http]
            [clojure.spec :as spec]
            [manifold.bus :as bus]
            [manifold.stream :as stream]
            [manifold.deferred :as d]
            [milo.game :as game]
            [milo.move :as move]
            [milo.card :as card]
            [milo.player :as player]
            [milo.server.connection :as conn]
            [milo.server.event :as event]
            [milo.server.http :refer [body-response
                                      handle-exceptions
                                      with-body
                                      non-websocket-response]]
            [milo.server.message :refer [decode
                                         encode]]
            [milo.server.model :as model]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(defmacro if-not-let
  ([bindings then]
   `(if-let ~bindings ~then nil))
  ([bindings then else]
   (let [form (bindings 0) tst (bindings 1)]
     `(let [temp# ~tst]
        (if-not temp#
          ~then
          (let [~form temp#]
            ~else))))))

(defn turn-taken?
  [status]
  (contains? #{:taken :round-over :game-over} status))

(defn take-turn
  [{:keys [event-manager games] :as deps} player game-id move]
  (dosync
   (if-not-let [game (get @games game-id)]
     {:milo/status :game-not-found}
     (let [{status :milo.game/status
            game' :milo.game/game} (game/take-turn game move)]
       (if-not (turn-taken? status)
         {:milo/status status}
         (let [event (event/store event-manager {:milo/status status
                                                 :milo.move/move move
                                                 :milo.game/game game'})]
           (alter games (fn [games] (assoc games game-id game')))
           event))))))

(defn handle-turn
  [{:keys [player-bus game-bus] :as deps} request]
  (handle-exceptions request
    (with-body [move :milo.move/move request]
      (let [{{game-id :id} :params
             {player "player"} :headers} request]
        (if-not (= player (:milo.player/id move))
          (body-response 403 request {:milo.server/message "Taking turns for other players is not allowed."})
          (let [{status :milo/status :as response} (take-turn deps player game-id move)]
            (cond
              (turn-taken? status) (let [opponent (game/opponent (:milo.game/game response) player)
                                         game-response (assoc response :milo/status status)
                                         player-event (-> response
                                                          (dissoc :milo.game/game)
                                                          (assoc :milo.game/id game-id))]
                                     (bus/publish! game-bus [game-id player] game-response)
                                     (bus/publish! game-bus [game-id opponent] game-response)
                                     (bus/publish! player-bus player player-event)
                                     (bus/publish! player-bus opponent player-event)
                                     (body-response 200 request game-response))
              (= status :game-not-found) (body-response 404 request {:milo/status :game-not-found
                                                                     :milo.game/id game-id})
              :else (body-response 409 request {:milo/status :turn-not-taken
                                                :milo.game/status status
                                                :milo.move/move move}))))))))

(defn update-existing
  [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn handle
  [{:keys [conn-manager games game-bus] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      (non-websocket-response)
      (let [conn-id (conn/add! conn-manager :game conn)
            conn-label (str "[game-ws-conn-" conn-id "] ")]
        (log/debug conn-label "Initial connection established.")
        (d/let-flow [initial-message (decode @(stream/take! conn))]
          (try
            (let [{game-id ::game/id player ::player/id} initial-message]
              (log/debug (str conn-label "Connecting player \"" player "\" to game " conn-id"."))
              ;; Give player current game state
              (let [state-message {:milo/status :connected
                                   ::game/status :state
                                   ::game/game (model/game-for player (get @games game-id))}]
                (stream/put! conn (encode state-message)))
              ;; Connect player to bus
              (stream/connect-via
               (bus/subscribe game-bus [game-id player])
               (fn [message]
                 (log/trace (str conn-label "Preparing message."))
                 (stream/put! conn (encode (update-existing
                                            message
                                            ::game/game
                                            (partial model/game-for player)))))
               conn)
              {:status 101})
            (catch Exception e
              (log/error e (str conn-label "Exception thrown while connecting player. Initial message: " initial-message))
              {:status 500})))))))

(defn websocket-handler
  [deps]
  (partial handle deps))

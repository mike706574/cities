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
            [milo.server.http :refer [body-response
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
  [{:keys [event-id games] :as deps} player game-id move]
  (dosync
   (if-not-let [game (get @games game-id)]
     {:milo/status :game-not-found}
     (let [{status :milo.game/status
            game' :milo.game/game} (game/take-turn game move)]
       (if-not (turn-taken? status)
         {:milo/status status}
         (let [event-id (alter event-id inc)]
           (alter games (fn [games] (assoc games game-id game')))
           {:milo/status status
            :milo/event-id event-id
            :milo.game/game game'}))))))

(defn handle-turn
  [{:keys [player-bus game-bus] :as deps} request]
  (with-body [move :milo.move/move request]
    (let [{{game-id :id} :params
           {player "player"} :headers} request ]
      (if-not (= player (:milo.player/id move))
        (body-response 403 {:milo.server/message "Taking turns for other players is not allowed."})
        (let [{status :milo/status
               event-id :milo/event-id
               game :milo.game/game} (take-turn deps player game-id move)]
          (cond
            (turn-taken? status) (let [opponent (game/opponent game player)
                                       game-event {:milo/event-id event-id
                                                   :milo/status status
                                                   :milo.move/move move
                                                   :milo.game/game game}
                                       player-event {:milo/event-id event-id
                                                     :milo/status status
                                                     :milo.game/id game-id
                                                     :milo.move/move move}]
                                   (bus/publish! game-bus [game-id player] game-event)
                                   (bus/publish! game-bus [game-id opponent] game-event)
                                   (bus/publish! player-bus player player-event)
                                   (bus/publish! player-bus opponent player-event)
                                   (body-response 200 request game-event))
            (= status :game-not-found) (body-response)
            :else (body-response 409 request {:milo/status status
                                              :milo.move/move move})))))))

(defn update-existing
  [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn websocket
  [{:keys [conn-manager games game-bus] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      (non-websocket-response)
      (let [conn-id (util/uuid)]
        (log/debug (str "Connection " conn-id " established."))
        (d/let-flow [id-body (decode @(stream/take! conn))
                     {game-id ::game/id player ::player/id} id-body
                     conn-id (conn/add! conn-manager player :game conn)]
          (log/debug (str "Player " player " connected to game " game-id ". [" conn-id "]"))
          ;; Give player current game state
          (stream/put! conn (encode {::game/status :connected
                                     ::game/game (model/game-for
                                                   player
                                                   (get @games game-id))}))
          ;; Connect player to bus
          (stream/connect-via
           (bus/subscribe game-bus [game-id player])
           (fn [message]
             (log/trace (str "Preparing message for " player "..."))
             (stream/put! conn (encode (update-existing
                                        message
                                        ::game/game
                                        (partial model/game-for player)))))
           conn)
          {:status 101})))))

(defn websocket-handler
  [deps]
  (partial websocket deps))

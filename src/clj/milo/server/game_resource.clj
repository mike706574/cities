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
                                      non-websocket-response
                                      not-acceptable
                                      parsed-body
                                      unsupported-media-type
                                      missing-header]]
            [milo.server.message :refer [decode
                                         encode]]
            [milo.server.model :as model]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(defn take-turn
  [{:keys [event-id games game-bus player-bus] :as deps} player request move]
  (dosync
   (let [game-id (get-in request [:params :id])]
     (if-let [game (get @games game-id)]
       (let [{status ::game/status game' ::game/game :as response} (game/take-turn game move)]
         (let [opponent (game/opponent game player)]
           (if (contains? #{:taken :round-over :game-over} status)
             (let [event-id (swap! event-id inc)]
               (log/debug (str "Turn taken - updating game."))
               (alter games (fn [games] (assoc games game-id game')))
               (bus/publish! game-bus [game-id opponent] {:milo/event-id event-id
                                                          :milo/status status
                                                          ::move/move move
                                                          ::game/game game'})
               (bus/publish! player-bus player {:milo/event-id event-id
                                                :milo/status status
                                                ::move/move move})
               (bus/publish! player-bus opponent {:milo/event-id event-id
                                                  :milo/status status
                                                  ::move/move move})
               (body-response 200 request {:milo/event-id event-id
                                           :milo/status status
                                           ::game/game (model/game-for player game')}))
             (body-response 409 request {:milo/status status
                                         :milo/event-id event-id}))))
       (body-response 404 request {:milo/status :game-not-found
                                   ::game/message (str "Game " game-id " not found.")})))))

(defn handle-turn
  [{:keys [games] :as deps} request]
  (or (unsupported-media-type request)
      (not-acceptable request)
      (missing-header request "player")
      (let [player (get-in request [:headers "player"])
            move (parsed-body request)]
        (if-not move
          (body-response 400 request {:message "Invalid request body."})
          (if-let [validation-failure (spec/explain-data ::move/move move)]
            (body-response 400 request {:message "Invalid move."
                                          :data validation-failure})
            (let [move-player (::player/id move)]
              (log/debug (str "Player: " player ", Move player: " move-player) )
              (if-not (= player move-player)
                (body-response 403 request {:message "Forbidden."})
                (take-turn deps player request move))))))))

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

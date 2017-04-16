(ns milo.server.game
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
                                      unsupported-media-type]]
            [milo.server.message :refer [decode
                                         encode]]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(defn player-model
  [state player]
  (if (game/game-over? state)
    state
    (let [{:keys [::game/players ::game/round ::game/previous-rounds]}  state
          {:keys [::game/draw-pile ::game/discard-piles]} round
          opponent (game/opponent state player)
          available-discards (into [] (comp (map val)
                                            (map last)
                                            (filter identity))
                                   discard-piles)
          round (-> round
                    (dissoc ::game/draw-pile ::game/discard-piles)
                    (update-in [::game/player-data opponent] dissoc ::player/hand)
                    (assoc ::game/available-discards available-discards)
                    (assoc ::game/draw-count (count draw-pile)))
          round-number (inc (count previous-rounds))]
      (assoc state ::game/round round ::game/round-number round-number))))

(defn take-turn
  [{games :games game-bus :game-bus :as deps} player request move]
  (dosync
   (let [accept (get-in request [:headers "accept"])
         game-id (get-in request [:params :id])]
     (if-let [game (get @games game-id)]
       (let [{status ::game/status game' ::game/state :as response} (game/take-turn game move)]
         (let [opponent (game/opponent game player)]
           (if (contains? #{:taken :round-over :game-over} status)
             (do (log/debug (str "Turn taken - updating game."))
                 (alter games (fn [games] (assoc games game-id game')))
                 (bus/publish! game-bus [game-id opponent] {::game/status status
                                                            ::move/move move
                                                            ::game/state game'})
                 (body-response 200 request {::game/status status
                                             ::game/state (player-model game' player)}))
             (body-response 409 request {::game/status status}))))
       (body-response 404 request {:message (str "Game " game-id " not found.")})))))

(defn handle-turn
  [{:keys [games] :as deps} player {headers :headers :as request}]
  (or (unsupported-media-type request)
      (not-acceptable request)
      (let [move (parsed-body request)]
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
                                     ::game/state (player-model
                                                   (get @games game-id)
                                                   player)}))
          ;; Connect player to bus
          (stream/connect-via
           (bus/subscribe game-bus [game-id player])
           (fn [message]
             (log/trace (str "Preparing message for " player "..."))
             (stream/put! conn (encode (update-existing
                                        message
                                        ::game/state
                                        #(player-model % player)))))
           conn)
          {:status 101})))))

(defn websocket-handler
  [deps]
  (partial websocket deps))

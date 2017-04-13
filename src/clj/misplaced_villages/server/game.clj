(ns misplaced-villages.server.game
  (:require [aleph.http :as http]
            [clojure.spec :as spec]
            [clojure.string :as str]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [misplaced-villages.card :as card]
            [misplaced-villages.game :as game]
            [misplaced-villages.move :as move]
            [misplaced-villages.score :as score]
            [misplaced-villages.player :as player]
            [misplaced-villages.server.message :refer [encode decode]]
            [misplaced-villages.server.util :as util]
            [taoensso.timbre :as log]))

(defn player-model
  [state player]
  (if (game/game-over? state)
    state
    (let [{:keys [::game/players ::game/round]}  state
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
                    (assoc ::game/draw-count (count draw-pile)))]
      (assoc state ::game/round round))))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn update-existing
  [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn take-action
  [games game-id action]
  (log/debug (str "Attempting move: " action))
  (dosync
   (let [game (get @games game-id)
         {:keys [::game/status
                 ::game/state] :as response} (game/take-turn game action)]
     (log/debug (str "Status: " status))
     (when (contains? #{:taken :round-over :game-over} status)
       (log/debug (str "Updating game."))
       (alter games (fn [games] (assoc games game-id state))))
     response)))

(defn process-action
  [games game-id player-id action-str]
  (let [game (get @games game-id)
        action (decode action-str)
        response (cond
                   (nil? game) {::game/status :no-game}
                   (nil? player-id) {::game/status :no-player}
                   (not (spec/valid? ::move/move action)) {::game/status :invalid-move
                                                           ::game/explanation (spec/explain-data ::move/move action)}
                   :else (take-action games game-id action))]
    (merge response {::game/id game-id
                     ::player/id player-id})))

(defn handle-action
  [{:keys [connections games game-bus]} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (let [conn-id (util/uuid)]
        (log/debug (str "Connection " conn-id " established."))
        (d/let-flow [id-body (decode @(s/take! conn))
                     {game-id ::game/id
                      player-id ::player/id} id-body]
          (log/debug (str "Player " player-id " connected to game " game-id "."))
          ;; Give player current game state
          (s/put! conn (encode {::game/status :connected
                                ::game/state (player-model
                                              (get @games game-id)
                                              player-id)}))
          ;; Connect player to bus
          (s/connect-via
           (bus/subscribe game-bus game-id)
           (fn [message]
             (log/debug (str "Preparing message for " player-id "..."))
             (s/put! conn (encode (update-existing
                                   message
                                   ::game/state
                                   #(player-model % player-id)))))
           conn)
          ;; Process all player actions
          (s/consume
           #(bus/publish! game-bus game-id %)
           (->> conn
                (s/map (partial process-action games game-id player-id))
                (s/buffer 100)))
          ;; Publish player connection
          (bus/publish! game-bus game-id
                        {::game/status :player-connected
                         ::player/id player-id})
          {:status 101})))))

(defn handler
  [deps]
  (partial handle-action deps))

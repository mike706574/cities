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
            [misplaced-villages.server.connection :as conn]
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

(defn consume-message
  [{:keys [player-bus game-bus games]} player game-id message]
  (let [message-id (util/uuid)]
    (try
      (log/trace (str "Consuming message " message-id) ".")
      (bus/publish! game-bus game-id (process-action games game-id player message))
      (catch Exception ex
        (log/error "Exception thrown while consuming message " message-id ".")
        (log/error ex)
        (bus/publish! player-bus player {:menu/status :error
                                         :menu/error-message (.getMessage ex)})))))

(defn handle-action
  [{:keys [conn-manager games game-bus] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (let [conn-id (util/uuid)]
        (log/debug (str "Connection " conn-id " established."))
        (d/let-flow [id-body (decode @(s/take! conn))
                     {game-id ::game/id player ::player/id} id-body
                     conn-id (conn/add! conn-manager player :game conn)]
          (log/debug (str "Player " player " connected to game " game-id ". [" conn-id "]"))
          ;; Give player current game state
          (s/put! conn (encode {::game/status :connected
                                ::game/state (player-model
                                              (get @games game-id)
                                              player)}))
          ;; Connect player to bus
          (s/connect-via
           (bus/subscribe game-bus game-id)
           (fn [message]
             (log/trace (str "Preparing message for " player "..."))
             (s/put! conn (encode (update-existing
                                   message
                                   ::game/state
                                   #(player-model % player)))))
           conn)
          ;; Process all player actions
          (s/consume (partial consume-message deps player game-id) conn)
          ;; Publish player connection
          (bus/publish! game-bus game-id
                        {::game/status :player-connected
                         ::player/id player})
          {:status 101})))))

(defn handler
  [deps]
  (partial handle-action deps))

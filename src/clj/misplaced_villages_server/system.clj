(ns misplaced-villages-server.system
  (:require [aleph.http :as http]
            [clojure.edn :as edn]
            [clojure.spec :as spec]
            [clojure.spec.test :as stest]
            [clojure.string :as str]
            [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [misplaced-villages.card :as card]
            [misplaced-villages.game :as game]
            [misplaced-villages.move :as move]
            [misplaced-villages-server.service :as service]
            [ring.middleware.params :as params]
            [ring.middleware.cors :as cors]
            [misplaced-villages.player :as player]
            [taoensso.timbre :as log]))

(stest/instrument)

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(def game-bus (bus/event-bus))

(defn update-existing
  [m k f]
  (if (contains? m k)
    (update m k f)
    m))

(defn take-action
  [games game-id action]
  (log/debug (str "Attempting move: " action))
  (let [game (get @games game-id)
        {:keys [::game/status
                ::game/state] :as response} (game/take-action game action)]
    (log/debug (str "Status: " status))
    (when (= status :taken)
      (log/debug (str "Updating game."))
      (swap! games (fn [games] (assoc games game-id state))))
    response))

(defn process-action
  [games game-id player-id action-str]
  (let [game (get @games game-id)
        action (edn/read-string action-str)
        response (cond
                   (nil? game) {::game/status :no-game}
                   (nil? player-id) {::game/status :no-player}
                   (not (spec/valid? ::move/move action)) {::game/status :invalid-move
                                                           ::game/explanation (spec/explain-data ::move/move action)}
                   :else (take-action games game-id action))]
    (merge response {::game/id game-id
                     ::player/id player-id})))

(defn handle-action
  [games req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [id-message (s/take! conn)
                   id-body (edn/read-string id-message)
                   {game-id ::game/id
                    player-id ::player/id} id-body]
        (println (str "Player " player-id " connected to game " game-id "."))
        ;; Give player current game state
        (s/put! conn (pr-str {::game/status :connected
                              ::game/state (game/for-player
                                            (get @games game-id)
                                            player-id)}))
        ;; Connect player to bus
        (s/connect-via
         (bus/subscribe game-bus game-id)
         (fn [message]
           (println (str "Preparing message for " player-id "..."))
           (s/put! conn (pr-str (update-existing
                                 message
                                 ::game/state
                                 #(game/for-player % player-id)))))
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
        {:status 101}))))

(defn action-handler
  [games]
  (partial handle-action games))

(defn handler
  [games]
  (-> (compojure/routes
       (GET "/game/:id" {{id :id} :params {player "player"} :headers}
            (if-let [game (get @games id)]
              (if player
                {:status 200
                 :headers {"Content-Type" "application/edn"}
                 :body (pr-str (game/for-player game player))}
                {:status 200
                 :headers {"Content-Type" "application/edn"}
                 :body (pr-str game)})
              {:status 404
               :headers {"Content-Type" "application/edn"}
               :body (pr-str {:message (str "Game " id " not found.")})}))
       (GET "/game-websocket" [] (action-handler games))
       (route/not-found "No such page."))
      (params/wrap-params)
      (cors/wrap-cors :access-control-allow-origin [#"http://192.168.1.141.*"]
                      :access-control-allow-methods [:get :put :post :delete])))

(defonce games (atom {"1" (game/rand-game ["Mike" "Abby"])}))

(defn system [config]
  {:app (service/aleph-service config (handler games))})

;; stuff
(def last-round #(-> % ::game/round))
(def turn #(-> % last-round ::game/turn))
(def hand-for #(-> % last-round ::game/player-data (get %2) ::player/hand))
(def expeditions-for #(-> % last-round ::game/player-data (get %2) ::player/expeditions))
(def cards-remaining #(-> % last-round ::game/draw-pile count))

(def game #(get @games "1"))

(comment
  (def conn @(http/websocket-client "ws://localhost:8000/game-websocket"))

  (turn (game))

  (hand-for (game) "Abby")

  (game/take-action (game) (move/exp* "Abby" (card/number :red 8)))
  (hand-for (game) "Mike")

  (def d (s/take! conn))
  (realized? d)
  @d

  (game)

  (s/put! conn (pr-str {::player/id "Abby" ::game/id "1"}))
  (s/put! conn (pr-str (move/move
                        "Abby"
                        (card/wager :yellow)
                        :expedition
                        :draw-pile)))


  (def response (s/take! conn))
  (-> (game-1) turn)
  (-> (game-1) (hand-for "Abby")))

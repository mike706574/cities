(ns misplaced-villages-server.system
  (:require [aleph.http :as http]
            [clojure.edn :as edn]
            [clojure.spec :as spec]
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
            [ring.middleware.cors :as cors]))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(def game-bus (bus/event-bus))

(defn process-action
  [game action-str]
  (let [action (edn/read-string action-str)]
    (if-not (spec/valid? ::move/move action)
      {::game/status :invalid-move}
      (do
        (println "Taking action...")
        (game/take-action game action)))))

(defn handle-action
  [games req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [player-id (s/take! conn)
                   game-id (s/take! conn)]
        (s/connect-via
         (bus/subscribe game-bus game-id)
         (fn [message]
           (println "Message:" message)
           (s/put! conn (pr-str message)))
         conn)
        (s/consume
         #(bus/publish! game-bus game-id %)
          (->> conn
              (s/map (partial process-action (get @games game-id)))
              (s/buffer 100)))))))

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

(defn system [config]
  (let [games (atom {"1" (game/start-game ["Mike" "Abby"])})]
    {:app (service/aleph-service config (handler games))}))

(comment
  (-> @(http/get "http://localhost:8000/game/1" {:throw-exceptions false})
      (:body)
      (slurp)
      (edn/read-string))

;;  (keys (ns-publics 'misplaced-villages.card))
  (def conn @(http/websocket-client "ws://localhost:8000/game-websocket"))
  (s/put-all! conn ["Abby" "1"])
  (s/put! conn (pr-str (move/move
                        "Abby"
                        (card/wager :gree)
                        :expedition
                        :draw-pile)))
  @(s/take! conn)

  @(s/take! conn1)   ;=> "Alice: hello"
  @(s/take! conn2)   ;=> "Alice: hello"

  (s/put! conn2 "hi!")

  @(s/take! conn1)   ;=> "Bob: hi!"
  @(s/take! conn2)   ;=> "Bob: hi!"
  )

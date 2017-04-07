(ns misplaced-villages-server.system
  (:require [clojure.string :as str]
            [compojure.core :as compojure :refer [GET]]
            [compojure.route :as route]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [misplaced-villages.game :as game]
            [misplaced-villages-server.action :as action]
            [misplaced-villages-server.menu :as menu]
            [misplaced-villages-server.service :as service]
            [ring.middleware.params :as params]
            [ring.middleware.cors :as cors]
            [ring.middleware.resource :as resource]
            [ring.util.response :as response]
            [taoensso.timbre :as log]))

(defn handler
  [{:keys [games invitations game-bus player-bus] :as deps}]
  (-> (compojure/routes
       (GET "/menu" []
            nil)
       (GET "/game/:id" {{id :id} :params {player "player"} :headers}
            (response/resource-response "game/index.html" {:root "public"}))
       (GET "/game-websocket" [] (action/handler games game-bus))
       (GET "/menu-websocket" [] (menu/handler deps))
       (route/not-found "No such page."))
      (resource/wrap-resource "public")
      (params/wrap-params)
      (cors/wrap-cors :access-control-allow-origin [#"http://192.168.1.141.*"]
                      :access-control-allow-methods [:get :put :post :delete])))

(defonce games (ref {"1" (game/rand-game ["Mike" "Abby"])}))
(defonce game-bus (bus/event-bus))
(defonce player-bus (bus/event-bus))

(defn system [config]
  (let [deps {:games games
              :invitations (ref #{})
              :game-bus game-bus
              :player-bus player-bus}]
    {:app (service/aleph-service config (handler deps))}))

(comment
  @games
  (def bus (bus/event-bus))
  (def source (s/stream))
  (def sink (s/stream))
  (def sink2 (s/stream))
  (s/connect
   (bus/subscribe bus 1)
   sink)

  (s/connect
   (bus/subscribe bus 1)
   sink2)

  (s/consume
   #(bus/publish! bus 1 %)
   source)

  (s/consume
   #(println "FROM SINK:" %)
   sink)
  (s/consume
   #(println "FROM SINK2:" %)
   sink2)

  (s/put! source "FObar331212")


  )

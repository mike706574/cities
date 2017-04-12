(ns misplaced-villages.server.system
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [clojure.string :as str]
            [compojure.core :as compojure :refer [GET ANY]]
            [compojure.route :as route]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [misplaced-villages.game :as game]
            [misplaced-villages.server.game :as game-resource]
            [misplaced-villages.server.menu :as menu-resource]
            [misplaced-villages.server.service :as service]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]
            [selmer.parser :as selmer]
            [taoensso.timbre :as log]))

(def users {"admin" {:username "admin"
                     :password (creds/hash-bcrypt "admin")
                     :roles #{::admin}}
            "mike" {:username "mike"
                    :password (creds/hash-bcrypt "mike")
                    :roles #{::user}}
            "abby" {:username "abby"
                    :password (creds/hash-bcrypt "abby")
                    :roles #{::user}}})

(derive ::admin ::user)

(defn routes
  [{:keys [games game-bus] :as deps}]
  (compojure/routes

       (GET "/" req
            (friend/authorize
             #{::user}
             (let [name (:current (friend/identity req))]
               (log/debug "Player:" name)
               (selmer/render-file "templates/menu.html" {:player name}))))

       (GET "/menu/:player" req
            (friend/authorize
             #{::user}
             (let [name (:current (friend/identity req))]
               (log/debug "Player:" name)
               (selmer/render-file "templates/menu.html" {:player name}))))

       (GET "/login" req
            (selmer/render-file
             "templates/login.html"
             {:anti-forgery-token
              ring.middleware.anti-forgery/*anti-forgery-token*}))

       (GET "/game/:id{[0-9]+}" {{id :id} :params :as req}
            (friend/authorize
             #{::user}
             (let [name (:current (friend/identity req))]
               (log/debug "Player:" name)
               (selmer/render-file "templates/game.html" {:player name
                                                          :game-id id}))))

       (GET "/game-websocket" [] (game-resource/handler games game-bus))

       (GET "/menu-websocket" [] (menu-resource/handler deps))

       (friend/logout (ANY "/logout" request
                           {:status 302
                            :headers {"Location" "/login"}
                            :body ""}))

       (route/not-found "No such page.")))

(defn handler
  [deps]
  (-> (routes deps)
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn users)
        :workflows [(workflows/interactive-form)]})
      (wrap-defaults site-defaults)))

(defonce games (ref {"1" (game/rand-game ["mike" "abby"])}))
(defonce game-bus (bus/event-bus))
(defonce player-bus (bus/event-bus))

(defn system [config]
  (let [invites (ref #{})
        deps {:games games
              :invites invites
              :game-bus game-bus
              :player-bus player-bus}]
    {:app (service/aleph-service config (handler deps))
     :games games
     :invites invites}))

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

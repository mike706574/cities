(ns milo.server.system
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.spec :as spec]
            [compojure.core :as compojure :refer [GET PUT ANY]]
            [compojure.route :as route]
            [manifold.bus :as bus]
            [milo.game :as game]
            [milo.move :as move]
            [milo.card :as card]
            [milo.player :as player]
            [milo.server.game-resource :as game-resource]
            [milo.server.menu-resource :as menu-resource]
            [milo.server.service :as service]
            [milo.server.connection :as conn]
            [milo.server.http :refer [unsupported-media-type
                                      not-acceptable
                                      parsed-body
                                      body-response]]
            [ring.middleware.defaults :refer [wrap-defaults
                                              site-defaults
                                              api-defaults]]
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

(defn api-routes
  [deps]
  (compojure/routes
   (PUT "/api/game/:id" request
        (log/debug "Request!")
        (let [player (get-in request [:headers "player"])
              response (game-resource/handle-turn deps player request)]
          (log/debug "Response!")
          response))

   (PUT "/api/game/:id" request
        (log/debug "Request!")
        (let [player (get-in request [:headers "player"])
              response (game-resource/handle-turn deps player request)]
          (log/debug "Response!")
          response))

   (route/not-found {:status 200})))

(defn api-handler
  [deps]
  (-> (api-routes deps)
      (wrap-defaults api-defaults)))

(defn site-routes
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

   (PUT "/foo" {{id :id} :params :as req}
        (println "HELLO")
        {:status 200
         :body "FOO"})

   (GET "/game-websocket" [] (game-resource/websocket-handler deps))

   (GET "/menu-websocket" [] (menu-resource/handler deps))

   (friend/logout (ANY "/logout" request
                       {:status 302
                        :headers {"Location" "/login"}
                        :body ""}))

   (route/not-found "No such page.")))

(defn site-handler
  [deps]
  (-> (site-routes deps)
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn users)
        :workflows [(workflows/interactive-form)]})
      (wrap-defaults site-defaults)))

(defn handler
  [deps]
  (let [api (api-handler deps)
        site (site-handler deps)]
    (fn [{uri :uri :as request}]
      (if (str/starts-with? uri "/api")
        (api request)
        (site request)))))

(def first-8
  [(card/number :yellow 7)
   (card/number :yellow 6)
   (card/number :yellow 10)
   (card/number :yellow 8)
   (card/wager :yellow 3)
   (card/number :green 3)
   (card/wager :blue 3)
   (card/number :green 10)])

(def second-8
  [(card/number :blue 3)
   (card/number :yellow 2)
   (card/number :blue 9)
   (card/number :white 9)
   (card/number :white 5)
   (card/number :yellow 9)
   (card/wager :white 2)
   (card/wager :white 1)])

(def last-4
  [(card/number :yellow 4)
   (card/number :blue 8)
   (card/wager :red 2)
   (card/wager :red 3)])

(def deck-1 (concat first-8 second-8 last-4))

(def test-game (game/game ["mike" "abby"] [deck-1 deck-1 deck-1] 4))

(defn system [config]
  (let [invites (ref #{})
        games (ref {})
        conns (atom {})
        conn-manager (conn/manager conns)
        event-id (atom 0)
        deps {:games games
              :invites invites
              :game-bus (bus/event-bus)
              :player-bus (bus/event-bus)
              :conn-manager conn-manager}]
    {:games games
     :invites invites
     :conn-manager conn-manager
     :handler (handler deps)
     :app (service/aleph-service config)}))

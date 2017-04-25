(ns milo.server.handler
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.spec :as spec]
            [compojure.core :as compojure :refer [GET PUT ANY POST DELETE]]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [manifold.bus :as bus]
            [milo.game :as game]
            [milo.move :as move]
            [milo.card :as card]
            [milo.player :as player]
            [milo.server.game-resource :as game-resource]
            [milo.server.menu-resource :as menu-resource]
            [milo.server.event :as event]
            [milo.server.connection :as conn]
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
   (GET "/api/game/:id" request (game-resource/handle-retrieval deps request))
   (PUT "/api/game/:id" request (game-resource/handle-turn deps request))
   (POST "/api/game" request (menu-resource/handle-accepting-invite deps request))
   (POST "/api/invite" request (menu-resource/handle-sending-invite deps request))
   (DELETE "/api/invite/:sender/:recipient" request (menu-resource/handle-deleting-invite deps request))
   (route/not-found {:status 200})))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri method] :as request}]
    (let [label (str "PUT \"" uri "\"")]
      (try
        (let [{:keys [status] :as response} (handler request)]
          (log/debug (str label " -> " status))
          response)
        (catch Exception e
          (log/error e label)
          {:status 500})))))

(defn api-handler
  [deps]
  (-> (api-routes deps)
      (wrap-defaults api-defaults)
      (wrap-logging)))

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

   (GET "/websocket" [] (menu-resource/websocket-handler deps))

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

(defprotocol HandlerFactory
  "Builds a request handler."
  (handler [this]))

(defrecord MiloHandlerFactory [games invites game-bus player-bus
                               event-manager conn-manager]
  HandlerFactory
  (handler [this]
    (let [api (api-handler this)
          site (site-handler this)]
      (fn [{uri :uri :as request}]
        (if (str/starts-with? uri "/api")
          (api request)
          (site request))))))

(defn factory
  []
  (component/using (map->MiloHandlerFactory {})
                   [:games :invites :game-bus :player-bus
                    :event-manager :conn-manager]))

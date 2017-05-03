(ns milo.server.handler
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [clojure.string :as str]
            [clojure.spec :as spec]
            [compojure.core :as compojure :refer [ANY DELETE GET POST PUT]]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [milo.api.user :as user-api]
            [milo.card :as card]
            [milo.server.resource :as resource]
            [milo.server.websocket :as websocket]
            [ring.middleware.defaults :refer [wrap-defaults
                                              site-defaults
                                              api-defaults]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as response]
            [selmer.parser :as selmer]
            [taoensso.timbre :as log]))

(defn api-routes
  [deps]
  (compojure/routes
   (GET "/api/game/:id" request (resource/handle-game-retrieval deps request))
   (PUT "/api/game/:id" request (resource/handle-turn deps request))
   (POST "/api/game" request (resource/handle-accepting-invite deps request))
   (GET "/api/menu" request (resource/handle-menu-retrieval deps request))
   (POST "/api/invite" request (resource/handle-sending-invite deps request))
   (GET "/api/invites" request (resource/handle-sending-invite deps request))
   (DELETE "/api/invite/:sender/:recipient" request (resource/handle-deleting-invite deps request))
   (route/not-found {:status 200})))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri method] :as request}]
    (let [label (str "PUT \"" uri "\"")]
      (try
        (log/debug label)
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
         #{:milo/user}
         (let [name (:current (friend/identity req))]
           (selmer/render-file "templates/client.html" {:player name}))))

   (GET "/login" req
        (selmer/render-file
         "templates/login.html"
         {:anti-forgery-token
          ring.middleware.anti-forgery/*anti-forgery-token*}))

   (GET "/websocket" [] (websocket/handler deps))

   (friend/logout (ANY "/logout" request
                       {:status 302
                        :headers {"Location" "/login"}
                        :body ""}))

   (route/not-found "No such page.")))

(defn site-handler
  [{:keys [user-manager] :as deps}]
  (-> (site-routes deps)
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn
                                (partial user-api/credentials user-manager))
        :workflows [(workflows/interactive-form)]})
      (wrap-resource "public")
      (wrap-defaults site-defaults)))

(defprotocol HandlerFactory
  "Builds a request handler."
  (handler [this]))

(defrecord MiloHandlerFactory [game-manager
                               invite-manager
                               game-bus
                               player-bus
                               event-manager
                               conn-manager
                               user-manager]
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
                   [:game-manager :invite-manager :game-bus :player-bus
                    :event-manager :conn-manager :user-manager]))

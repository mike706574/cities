(ns milo.server.handler
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [milo.server.api.handler :as api-handler]
            [milo.server.site.handler :as site-handler]))

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
    (let [api (api-handler/handler this)
          site (site-handler/handler this)]
      (fn [{uri :uri :as request}]
        (if (str/starts-with? uri "/api")
          (api request)
          (site request))))))

(defn factory
  []
  (component/using (map->MiloHandlerFactory {})
                   [:game-manager :invite-manager :game-bus :player-bus
                    :event-manager :conn-manager :user-manager]))

(ns cities.backend.system
  (:require [manifold.bus :as bus]
            [cities.data :as data]
            [cities.game :as game]
            [cities.card :as card]
            [cities.backend.event :as event-api]
            [cities.backend.game :as game-api]
            [cities.backend.invite :as invite-api]
            [cities.backend.user :as user]
            [cities.backend.connection :as conn]
            [cities.backend.handler :as handler]
            [cities.backend.service :as service]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(defn system
  [config]
  (log/info "Building system.")
  (component/system-map
   :player-bus (bus/event-bus)
   :game-bus (bus/event-bus)

   :connections (atom {})
   :events (ref {})
   :active-games (ref {})
   :completed-games (ref {})

   :invites (ref #{})
   :users (atom {})

   :conn-manager (conn/manager)

   :event-manager (event-api/manager)
   :game-manager (game-api/manager)
   :invite-manager (invite-api/manager)
   :user-manager (user/manager)

   :handler-factory (handler/factory)
   :app (service/aleph-service config)))

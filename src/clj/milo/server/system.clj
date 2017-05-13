(ns milo.server.system
  (:require [cemerick.friend.credentials :as creds]
            [manifold.bus :as bus]
            [milo.api.event :as event-api]
            [milo.api.game :as game-api]
            [milo.api.invite :as invite-api]
            [milo.api.user :as user]
            [milo.data :as data]
            [milo.game :as game]
            [milo.card :as card]
            [milo.server.connection :as conn]
            [milo.server.handler :as handler]
            [milo.server.service :as service]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(def users
  (atom {"admin" {:username "admin"
                  :password (creds/hash-bcrypt "admin")
                  :roles #{:milo/admin}
                  :avatar "user.jpg"}
         "mike" {:username "mike"
                 :password (creds/hash-bcrypt "mike")
                 :roles #{:milo/user}
                 :avatar "cookie.jpg"}
         "abby" {:username "abby"
                 :password (creds/hash-bcrypt "abby")
                 :roles #{:milo/user}
                 :avatar "user.jpg"}
         "guest" {:username "guest"
                  :password (creds/hash-bcrypt "guest")
                  :roles #{:milo/user}
                  :avatar "user.jpg"}}))

(derive :milo/admin :milo/user)

(defn system
  [config]
  (log/info "Building system.")
  (log/merge-config!
   {:appenders {:spit (appenders/spit-appender
                       {:fname "/home/mike/milo-webapp.log"})}})
  {:player-bus (bus/event-bus)
   :game-bus (bus/event-bus)

   :connections (atom {})
   :events (ref {})
   :active-games (ref {})
   :completed-games (ref {})

   :invites (ref #{})
   :users users

   :conn-manager (conn/manager)

   :event-manager (event-api/manager)
   :game-manager (game-api/manager)
   :invite-manager (invite-api/manager)
   :user-manager (user/manager)

   :handler-factory (handler/factory)
   :app (service/aleph-service config)})

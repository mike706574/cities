(ns cities.server.system
  (:require [cemerick.friend.credentials :as creds]
            [manifold.bus :as bus]
            [cities.api.event :as event-api]
            [cities.api.game :as game-api]
            [cities.api.invite :as invite-api]
            [cities.api.user :as user]
            [cities.data :as data]
            [cities.game :as game]
            [cities.card :as card]
            [cities.server.connection :as conn]
            [cities.server.handler :as handler]
            [cities.server.service :as service]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]))

(def users
  (atom {"admin" {:username "admin"
                  :password (creds/hash-bcrypt "admin")
                  :roles #{:cities/admin}
                  :avatar "user.jpg"}
         "mike" {:username "mike"
                 :password (creds/hash-bcrypt "mike")
                 :roles #{:cities/user}
                 :avatar "cookie.jpg"}
         "abby" {:username "abby"
                 :password (creds/hash-bcrypt "abby")
                 :roles #{:cities/user}
                 :avatar "user.jpg"}
         "guest" {:username "guest"
                  :password (creds/hash-bcrypt "guest")
                  :roles #{:cities/user}
                  :avatar "user.jpg"}}))

(derive :cities/admin :cities/user)

(defn system
  [config]
  (log/info "Building system.")
  (log/merge-config!
   {:appenders {:spit (appenders/spit-appender
                       {:fname "/home/mike/cities-webapp.log"})}})
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

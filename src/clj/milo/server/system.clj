(ns milo.server.system
  (:require [cemerick.friend.credentials :as creds]
            [manifold.bus :as bus]
            [milo.game :as game]
            [milo.card :as card]
            [milo.server.handler :as handler]
            [milo.server.event :as event]
            [milo.server.connection :as conn]
            [milo.server.service :as service]
            [taoensso.timbre :as log]))

(def users {"admin" {:username "admin"
                     :password (creds/hash-bcrypt "admin")
                     :roles #{:milo/admin}}
            "mike" {:username "mike"
                    :password (creds/hash-bcrypt "mike")
                    :roles #{:milo/user}}
            "abby" {:username "abby"
                    :password (creds/hash-bcrypt "abby")
                    :roles #{:milo/user}}})

(derive :milo/admin :milo/user)

(defn system
  [config]
  (log/info "Building system.")
  {:player-bus (bus/event-bus)
   :game-bus (bus/event-bus)

   :connections (atom {})
   :events (ref {})
   :users users

   :games (ref {})
   :invites (ref #{})

   :event-manager (event/manager)
   :conn-manager (conn/manager)

   :handler-factory (handler/factory)
   :app (service/aleph-service config)})

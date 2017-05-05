(ns milo.server.system
  (:require [cemerick.friend.credentials :as creds]
            [manifold.bus :as bus]
            [milo.api.event :as event-api]
            [milo.api.game :as game-api]
            [milo.api.invite :as invite-api]
            [milo.api.user :as user]
            [milo.game :as game]
            [milo.card :as card]
            [milo.server.connection :as conn]
            [milo.server.handler :as handler]
            [milo.server.service :as service]
            [taoensso.timbre :as log]))

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

(defn system
  [config]
  (log/info "Building system.")
  {:player-bus (bus/event-bus)
   :game-bus (bus/event-bus)

   :connections (atom {})
   :events (ref {})
   :active-games (ref {"1" test-game})
   :completed-games (ref {})

   :invites (ref #{["mike" "abby"]})
   :users users

   :conn-manager (conn/manager)

   :event-manager (event-api/manager)
   :game-manager (game-api/manager)
   :invite-manager (invite-api/manager)
   :user-manager (user/manager)

   :handler-factory (handler/factory)
   :app (service/aleph-service config)})

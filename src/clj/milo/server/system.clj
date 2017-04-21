(ns milo.server.system
  (:require [manifold.bus :as bus]
            [milo.game :as game]
            [milo.card :as card]
            [milo.server.game-resource :as game-resource]
            [milo.server.menu-resource :as menu-resource]
            [milo.server.handler :as handler]
            [milo.server.event :as event]
            [milo.server.connection :as conn]
            [milo.server.service :as service]
            [taoensso.timbre :as log]))

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
  {:player-bus (bus/event-bus)
   :game-bus (bus/event-bus)

   :connections (ref {})
   :events (ref {})

   :games (ref {})
   :invites (ref #{})

   :event-manager (event/manager)
   :conn-manager (conn/manager)

   :handler-factory (handler/factory)
   :app (service/aleph-service config)})

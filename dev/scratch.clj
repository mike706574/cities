(ns scratch
  (:require [user :refer [system]]
            [aleph.http :as http]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :as repl]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [cities.card :as card]
            [cities.game :as game]
            [cities.player :as player]
            [cities.backend.system :as system]
            [cities.backend.message :as message]
            [taoensso.timbre :as log]))

(defn connections [] @(:connections system))

(defn invites [] @(:invites system))

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

(defn parse
  [request]
  (if (contains? request :body)
    (update request :body (comp message/decode slurp))
    request))

(comment

  (parse @(http/get "http://localhost:8001/api/menu"
                    {:headers {"Player" "abby"
                               "Accept" "application/transit+json"}
                     :throw-exceptions false}) )
  )

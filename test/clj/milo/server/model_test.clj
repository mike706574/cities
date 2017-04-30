(ns milo.server.model-test
  (:require [clojure.test :refer [deftest testing is]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [milo.card :as card]
            [milo.game :as game]
            [milo.server.model :as model]
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

(deftest menu-game
  (is (= [{} {"1" #:milo.game{:id nil,
                              :opponent "abby",
                              :over? false,
                              :loaded? false,
                              :round-number 1,
                              :turn "mike"}}]
         (model/game-summaries-for "mike" {"1" test-game}))))

(deftest one-game
  (is (= [#{["mike" "abby"]}
          #{["bob" "mike"]}]
         (model/invites-for "mike" #{["mike" "abby"]
                                     ["bob" "mike"]}))))

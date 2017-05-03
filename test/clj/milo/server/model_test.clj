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

(def test-game (-> (game/game ["mike" "abby"] [deck-1 deck-1 deck-1] 4)
                   (assoc :milo.game/id 1)))

(deftest summarize-game-for
  (is (= #:milo.game{:id 1,
                     :opponent "abby",
                     :over? false,
                     :loaded? false,
                     :round-number 1,
                     :round {:milo.game/turn "mike"}}
         (model/summarize-game-for "mike" test-game))))

(deftest one-game
  (is (= [#{["mike" "abby"]}
          #{["bob" "mike"]}]
         (model/separate-invites-by-direction "mike" #{["mike" "abby"]
                                                       ["bob" "mike"]}))))

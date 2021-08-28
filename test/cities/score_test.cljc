(ns cities.score-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :refer :all]
   [clojure.test.check :as tc]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as prop]
   [cities.game :as game]
   [cities.card :as card]
   [cities.player :as player]
   [cities.score :as score]
   [clojure.test :as test]))

(deftest expedition-score-1
  (is
   (= 0 (score/expedition-score []))
   "If no cards are placed in an expedition, no costs are incurred."))

(deftest expedition-score-2
  (is
   (= -20 (score/expedition-score [(card/wager-1 :blue)]))
   "Starting an expedition costs 20 points."))

(deftest expedition-score-3
  (is
   (= 0 (score/expedition-score [(card/number :blue 2)
                                (card/number :blue 8)
                                (card/number :blue 10)]))
   "Number add points to the expedition."))

(deftest expedition-score-4
  (is
   (= 0 (score/expedition-score [(card/wager-1 :blue)
                                (card/number :blue 10)]))
   "One wager card multiples the score by 2."))

(deftest expedition-score-5
  (is
   (= 10 (score/expedition-score [(card/wager-1 :blue)
                                 (card/wager-2 :blue)
                                 (card/number :blue 10)]))
   "Two wager cards multiply the score by 3."))

(deftest expedition-score-6
  (is
   (= 20 (score/expedition-score [(card/wager-1 :blue)
                                 (card/wager-2 :blue)
                                 (card/wager-3 :blue)
                                 (card/number :blue 10)]))
   "Three wager cards multiply the score by 4."))

(deftest expedition-score-7
  (is (= 80 (score/expedition-score [(card/wager-3 :blue)
                                    (card/wager-2 :blue)
                                    (card/wager-1 :blue)
                                    (card/number :blue 2)
                                    (card/number :blue 3)
                                    (card/number :blue 4)
                                    (card/number :blue 5)
                                    (card/number :blue 6)]))
      "If an expedition contains 8 or more cards, a bonus of 20 is awarded."))

(def round {::game/player-data
            {"Mike" {::player/expeditions
                     {:blue [(card/wager-3 :blue)
                             (card/wager-1 :blue)
                             (card/number :blue 4)
                             (card/number :blue 5)
                             (card/number :blue 6)]
                      :red [(card/number :red 3)
                            (card/number :blue 4)
                            (card/number :blue 10)]
                      :green []
                      :yellow []
                      :white []}}
             "Abby" {::player/expeditions
                     {:blue []
                      :red []
                      :green [(card/number :green 10)]
                      :yellow [(card/wager-1 :yellow)
                               (card/wager-2 :yellow)
                               (card/wager-3 :yellow)
                               (card/number :yellow 2)
                               (card/number :yellow 3)
                               (card/number :yellow 4)
                               (card/number :yellow 5)
                               (card/number :yellow 6)
                               (card/number :yellow 7)
                               (card/number :yellow 8)
                               (card/number :yellow 9)
                               (card/number :yellow 10)]
                      :white []}}}})

(deftest round-scores
  (is (= {"Mike" 22
          "Abby" 206}
         (score/round-scores round))))

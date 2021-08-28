(ns cities.player-test
  (:require
   [clojure.spec.test.alpha :as stest]
   [clojure.spec.gen.alpha :as gen]
   [cities.player :as player]))

(stest/instrument)

(stest/check 'cities.player/uniform-color?)
(stest/check 'cities.player/wagers-before-numbers?)
(stest/check 'cities.player/under-wager-limit?)
(stest/check 'cities.player/numbers-in-order?)

(->> (stest/enumerate-namespace 'cities.player)
     (stest/check))

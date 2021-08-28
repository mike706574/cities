(ns cities.game-test
  (:require
   [clojure.test :refer :all]
   [cities.card :as card]
   [cities.data :as data]
   [cities.game :as game]
   [cities.player :as player]))

(deftest possible-moves-1
  (is
   (= [(game/move "Mike" (card/wager-1 :blue) :discard-pile :blue)
       (game/move "Mike" (card/wager-1 :blue) :discard-pile :draw-pile)
       (game/move "Mike" (card/wager-1 :blue) :expedition :draw-pile)]
      (data/possible-moves
       {::game/turn "Mike"
        ::game/moves []
        ::game/discard-piles card/empty-piles
        ::game/draw-pile []
        ::game/players ["Mike" "Abby"]
        ::game/player-data {"Mike" {::player/hand [(card/wager-1 :blue)]
                                    ::player/expeditions card/empty-piles}}})))
  "If we have a wager card and an expedition has no number card, the wager card can be played.")

(deftest possible-moves-2
  (is
   (= [(game/move "Mike" (card/wager-1 :blue) :discard-pile :blue)
       (game/move "Mike" (card/wager-1 :blue) :discard-pile :draw-pile)]
      (data/possible-moves
       {::game/turn "Mike"
        ::game/moves []
        ::game/discard-piles card/empty-piles
        ::game/draw-pile []
        ::game/player-data {"Mike" {::player/hand [(card/wager-1 :blue)]
                                    ::player/expeditions {:green []
                                                          :red []
                                                          :blue [(card/number :blue 2)]
                                                          :white []
                                                          :yellow []}}}}))
   "If there is a number card in an expedition, no wager card can be played."))

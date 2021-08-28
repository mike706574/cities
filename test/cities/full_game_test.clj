(ns cities.full_game-test
  (:require
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clojure.test :refer [deftest testing is]]
   [cities.card :as card]
   [cities.player :as player]
   [cities.game :as game]
   [clojure.walk :as walk]
   [clojure.test :as test]
   [cities.data :as data]))

(stest/instrument)

(def deck-2
  [(card/number :red 4)
   (card/wager :yellow 1)
   (card/number :white 4)
   (card/wager :green 3)
   (card/wager :blue 1)
   (card/number :yellow 9)
   (card/number :red 7)
   (card/number :green 6)
   (card/number :white 2)
   (card/wager :white 2)
   (card/number :white 7)
   (card/number :red 10)
   (card/number :green 2)
   (card/wager :yellow 2)
   (card/number :yellow 8)
   (card/number :blue 7)
   (card/number :red 5)
   (card/number :yellow 5)
   (card/number :white 10)
   (card/number :white 9)])

(def deck-3
  [(card/number :blue 7)
   (card/wager :red 2)
   (card/number :green 10)
   (card/number :white 3)
   (card/number :red 4)
   (card/number :white 2)
   (card/number :green 6)
   (card/wager :white 1)
   (card/number :white 5)
   (card/wager :blue 1)
   (card/number :red 9)
   (card/number :yellow 3)
   (card/wager :yellow 2)
   (card/number :green 9)
   (card/number :white 6)
   (card/number :blue 9)
   (card/wager :blue 2)
   (card/number :red 10)
   (card/number :white 7)
   (card/number :red 3)])

(def card? #(and (map? %) (= #{::card/type ::card/color ::card/number} (set (keys %)))))

(def cardify #(if (card? %) (card/literal-card %) %))
(def vectorify #(if (list? %) (vec %) %))

(defn walk
  [x]
  (walk/postwalk (comp cardify vectorify) x))

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

(defmacro breakdown
  [response & body]
  `(let [~'status (::game/status ~response)
         ~'game (::game/game ~response)
         ~'move (::game/move ~response)
         ~'drawn-card (::game/drawn-card ~response)

         ~'players (::game/players ~'game)
         ~'round (::game/round ~'game)
         ~'past-rounds (::game/past-rounds ~'game)
         ~'remaining-rounds (::game/remaining-rounds ~'game)

         ;; Last Round
         ~'last-round (last ~'past-rounds)
         ~'last-round-cards (when ~'last-round (game/round-cards ~'last-round))
         ~'last-discard-piles (::game/discard-piles ~'last-round)
         ~'last-moves (::game/moves ~'last-round)
         ~'last-draw-pile (::game/draw-pile ~'last-round)
         ~'last-player-data (::game/player-data ~'last-round)

         ;; Current Round
         ~'turn (::game/turn ~'round)
         ~'cards (game/round-cards ~'round)
         ~'discard-piles (::game/discard-piles ~'round)
         ~'moves (::game/moves ~'round)
         ~'draw-pile (::game/draw-pile ~'round)
         ~'player-data (::game/player-data ~'round)

         ;; Mike
         ~'mike-data (get ~'player-data "mike")
         ~'mike-hand (::player/hand ~'mike-data)
         ~'mike-expeditions (::player/expeditions ~'mike-data)

         ;; Abby
         ~'abby-data (get ~'player-data "abby")
         ~'abby-hand (::player/hand ~'abby-data)
         ~'abby-expeditions (::player/expeditions ~'abby-data)]
     ~@body))

(def test-game (game/game ["mike" "abby"] [deck-1 deck-2 deck-3] 4))
(def test-response {::game/status :initial ::game/game test-game})

(deftest initial-response
  (breakdown test-response
    (is (= ["mike" "abby"] players))
    (is (= "mike" turn))
    (is (empty? past-rounds))
    (is (= 2 (count remaining-rounds)))
    (is (= (set deck-1) cards))
    (is (= first-8 mike-hand))
    (is (= second-8 abby-hand))
    (is (= last-4 draw-pile))
    (is (= {:white [] :yellow [] :green [] :red [] :blue []}
           discard-piles))
    (is (= {:white [] :yellow [] :green [] :red [] :blue []}
           mike-expeditions))
    (is (= {:white [] :yellow [] :green [] :red [] :blue []}
           abby-expeditions))))

(deftest rejections
  (is (= :invalid-player
         (::game/status (game/take-turn
                         test-game
                         (game/exp* "bob" (card/wager :yellow 3))))))

  (is (= :wrong-player
         (::game/status (game/take-turn
                         test-game
                         (game/exp* "abby" (card/wager :yellow 3))))))

  (is (= :card-not-in-hand
         (::game/status (game/take-turn
                         test-game
                         (game/exp* "mike" (card/number :green 6))))))

  (is (= :card-not-in-hand
         (::game/status (game/take-turn
                         test-game
                         (game/disc* "mike" (card/number :green 6))))))

  (is (= :discard-pile-empty
         (::game/status (game/take-turn
                         test-game
                         (game/disc "mike" (card/wager :yellow 3) :green))))))

(defn take-turns
  [response [head & tail]]
  (if head
    (let [{status ::game/status :as out} (game/take-turn (::game/game response) head)]
      (if (= status :taken)
        (recur out tail)
        out))
    response))

(deftest taking-a-turn
  (let [test-move (game/exp* "mike" (card/wager :yellow 3))]
    (breakdown (game/take-turn test-game test-move)
      (is (= :taken status))
      (is (= test-move move))
      (is (= (card/number :yellow 4) drawn-card))
      (is (= [test-move] moves))
      (is (= ["mike" "abby"] players))
      (is (= "abby" turn))
      (is (empty? past-rounds))
      (is (= 2 (count remaining-rounds)))
      (is (= (set deck-1) cards))
      (is (= [(card/number :yellow 4)
              (card/number :yellow 7)
              (card/number :yellow 6)
              (card/number :yellow 10)
              (card/number :yellow 8)
              (card/number :green 3)
              (card/wager :blue 3)
              (card/number :green 10)]
             mike-hand))
      (is (= second-8 abby-hand))
      (is (= [(card/number :blue 8)
              (card/wager :red 2)
              (card/wager :red 3)]
             draw-pile))
      (is (= {:white [] :yellow [] :green [] :red [] :blue []}
             discard-piles))
      (is (= {:white [] :yellow [(card/wager :yellow 3)] :green [] :red [] :blue []}
             mike-expeditions))
      (is (= {:white [] :yellow [] :green [] :red [] :blue []}
             abby-expeditions)))))

(def test-moves [(game/exp* "mike" (card/wager :yellow 3))
                 (game/disc* "abby" (card/number :white 5))])

(deftest two-turns
  (breakdown (take-turns test-response test-moves)
    (is (= :taken status))
    (is (= moves test-moves))
    (is (= "mike" turn))))

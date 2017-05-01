(ns milo.server.model
  (:require [milo.game :as game]
            [milo.menu :as menu]
            [milo.player :as player]))

(defn divider
  [f]
  (fn divide
    ([] (vector (list) (list)))
    ([out] out)
    ([out x]
     (update out (if (f x) 0 1) conj x))))

(defn game-for
  [player game]
  (if (game/game-over? game)
    game
    (let [{:keys [::game/players ::game/round ::game/previous-rounds]}  game
          {:keys [::game/draw-pile ::game/discard-piles]} round
          opponent (game/opponent game player)
          available-discards (into [] (comp (map val)
                                            (map last)
                                            (filter identity))
                                   discard-piles)
          round (-> round
                    (dissoc ::game/draw-pile ::game/discard-piles)
                    (update-in [::game/player-data opponent] dissoc ::player/hand)
                    (assoc ::game/available-discards available-discards)
                    (assoc ::game/draw-count (count draw-pile)))]
      (assoc game ::game/round round ::game/opponent opponent))))

(defn game-summary-for
  [player game]
  (let [id (:milo.game/id game)
        opponent (game/opponent game player)
        base #:milo.game{:id id
                         :opponent opponent
                         :loaded? false}]
    (if (game/game-over? game)
      (assoc base :milo.game/over? true)
      (assoc base
             :milo.game/round (select-keys (:milo.game/round game) [::game/turn])
             :milo.game/round-number (inc (count (:milo.game/past-rounds game)))))))

(defn playing?
  [player game]
  (some #{player} (::game/players game)))

(defn invites-for
  [player invites]
  (loop [[head & tail] invites
         out [#{} #{}]]
    (if head
      (if (some #{player} head)
        (recur tail (update out (if (= (first head) player) 0 1) conj head))
        (recur tail out))
      out)))

(defn game-summaries-for
  [player games]
  (let [playing? (fn [[id game]]
                   (playing? player game))
        game-summary-for (fn [[id game]] [id (game-summary-for player game)])
        xform (comp (filter playing?)
                    (map game-summary-for))
        divide (divider (fn [[id game]]
                          (::game/over? game)))]
    (map #(into {} %) (transduce xform divide games))))

(defn menu-for
  [player games invites]
  (let [[completed-games
         active-games] (game-summaries-for player games)
        [sent-invites
         received-invites] (invites-for player invites)]
    {:milo.player/id player
     :milo/completed-games completed-games
     :milo/active-games active-games
     :milo/sent-invites sent-invites
     :milo/received-invites received-invites}))

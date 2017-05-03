(ns milo.server.model
  (:require [milo.game :as game]
            [milo.player :as player]))

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

(defn summarize-game-for
  [player game]
  (let [id (:milo.game/id game)
        opponent (game/opponent game player)
        base #:milo.game{:id id
                         :opponent opponent
                         :loaded? false}]
    (if (game/game-over? game)
      (assoc base :milo.game/over? true)
      (assoc base
             :milo.game/over? false
             :milo.game/round (select-keys (:milo.game/round game) [::game/turn])
             :milo.game/round-number (inc (count (:milo.game/past-rounds game)))))))

(defn separate-games-by-status
  [games]
  (loop [[head & tail] games
         out [{} {}]]
    (if head
      (update out (if (game/game-over? head) 0 1) assoc head)
      out)))

(defn separate-invites-by-direction
  [player invites]
  (loop [[head & tail] invites
         out [#{} #{}]]
    (if head
      (recur tail (update out (if (= (first head) player) 0 1) conj head))
      out)))

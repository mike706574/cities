(ns cities.data
  (:require
   #?(:clj [clojure.spec.alpha :as s]
      :cljs [cljs.spec.alpha :as s])
   [clojure.string :as str]
   [cities.card :as card]
   [cities.player :as player]
   [cities.misc :as misc]
   [cities.game :as game]
   [cities.score :as score]))

(defn potential-moves
  "Returns all moves that would be possible without factoring in expedition and
  discard pile games."
  [round]
  (let [turn (::game/turn round)
        hand (get-in round [::game/player-data turn ::player/hand])
        combos (misc/cartesian-product hand game/destinations game/sources)]
    (map #(apply game/move turn %) combos)))

(defn possible-moves
  "Returns all moves that are possible when factoring in expedition and discard
  pile games."
  [round]
  (let [potential-moves (potential-moves round)
        possible-moves (filter
                        (fn [potential-move]
                          (let [issue (game/validate-move round potential-move)]
                            (nil? issue)))
                        potential-moves)]
    possible-moves))

(defn hand
  [game player]
  (get-in game [::game/round ::game/player-data player ::player/hand]))

(defn discards
  ([game]
   (get-in game [::game/round ::game/discard-piles]))
  ([game color]
   (get-in game [::game/round ::game/discard-piles color])))

(defn turn [game] (-> game ::game/round ::game/turn))

(defn move-sentence
  "Builds a English sentence describing a move from the perspective of a player."
  [player {:keys [:cities.player/id :cities.card/card :cities.game/destination :cities.game/source]}]
  (str (if (= player id)
         "You"
         id)
       " "
       (case destination
         :expedition "played"
         :discard-pile "discarded")
       " "
       (card/description card)
       " and drew "
       (if (= :draw-pile source)
         "a new card."
         (str "from the " (-> source name str/capitalize) " discard pile."))))

(defn turn-sentence
  "Builds a English sentence describing a turn."
  [{:keys [:cities.player/id
           :cities.card/card
           :cities.game/destination
           :cities.game/source]} drawn-card]
  (str id
       " "
       (case destination
         :expedition "played"
         :discard-pile "discarded")
       " "
       (card/label card)
       " and drew "
       (card/label drawn-card)
       (if (= :draw-pile source)
         (str " from the draw pile.")
         (str " from the " (-> source name str/capitalize) " discard pile."))))

(defn rand-turns
  [game]
  (lazy-seq
   (when-not (game/game-over? game)
     (let [move (rand-nth (possible-moves (:cities.game/round game)))]
       (let [{status :cities.game/status
              game' :cities.game/game
              drawn-card :cities.game/drawn-card} (game/take-turn game move) ]
         (cons {:status status
                :move move
                :drawn-card drawn-card
                :game game}
               (rand-turns game')))))))

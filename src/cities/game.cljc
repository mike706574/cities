(ns cities.game
  (:require
   #?(:clj [clojure.spec.alpha :as s]
      :cljs [cljs.spec.alpha :as s])
   [clojure.string :as str]
   [cities.card :as card]
   [cities.player :as player]
   [cities.misc :as misc]))

(s/def ::player-data (s/map-of ::player/id ::player/data :count 2))
(s/def ::players (s/tuple ::player/id ::player/id))

(s/def ::discard-piles ::card/color-piles)
(s/def ::turn ::player/id)

(s/def ::draw-pile (s/coll-of ::card/card))

;; A move consists of two phases: placing and drawing
(def destinations #{:expedition :discard-pile})
(def sources (conj card/colors :draw-pile))
(s/def ::source sources)
(s/def ::destination destinations)
(s/def ::move (s/keys :req [::player/id
                            ::card/card
                            ::destination
                            ::source]))

(defn move
  [player-id card destination source]
  {::player/id player-id
   ::card/card card
   ::destination destination
   ::source source})

(s/fdef move
  :args (s/cat :player-id ::player/id
               :card ::card/card
               :destination ::destination
               :source ::source)
  :ret ::move)

(defn exp
  [player-id card source]
  {::player/id player-id
   ::card/card card
   ::destination :expedition
   ::source source})

(s/fdef exp
  :args (s/cat :player-id ::player/id
               :card ::card/card
               :source ::source)
  :ret ::move)

(defn exp*
  [player-id card]
  {::player/id player-id
   ::card/card card
   ::destination :expedition
   ::source :draw-pile})

(s/fdef exp*
  :args (s/cat :player-id ::player/id
               :card ::card/card)
  :ret ::move)

(defn disc
  [player-id card source]
  {::player/id player-id
   ::card/card card
   ::destination :discard-pile
   ::source source})

(s/fdef disc
  :args (s/cat :player-id ::player/id
               :card ::card/card
               :source ::source)
  :ret ::move)

(defn disc*
  [player-id card]
  {::player/id player-id
   ::card/card card
   ::destination :discard-pile
   ::source :draw-pile})

(s/fdef disc*
  :args (s/cat :player-id ::player/id
               :card ::card/card)
  :ret ::move)

(s/def ::moves (s/* ::move))

(def statuses #{:game-over
                :round-over
                :invalid-player
                :wrong-player
                :card-not-in-hand
                :expedition-underway
                :discard-pile-empty
                :too-low
                :taken})
(s/def ::status statuses)

;; A round
(defn player-cards
  "Collects all cards from every player's hand and expeditions."
  [{:keys [::player/hand ::player/expeditions]}]
  (into hand (card/combine-piles expeditions)))

(defn all-player-cards
  "Collects all cards from every player's hand and expeditions."
  [player-data]
  (flatten
   (map player-cards (vals player-data))))

(defn round-cards
  "Collects all cards for a round."
  [{:keys [::player-data ::draw-pile ::discard-piles]}]
  (-> (set draw-pile)
      (into (card/combine-piles discard-piles))
      (into (all-player-cards player-data))))

(s/fdef round-cards
    :args (s/cat :round ::round)
    :ret (s/coll-of ::card/card))

(s/def ::round
  (s/and
   (s/keys :req [::turn
                 ::player-data
                 ::discard-piles
                 ::moves
                 ::draw-pile])
;;   #(= (round-cards %) card/deck)
   ))

(s/def ::past-rounds (s/coll-of ::round :max-count 3))
(s/def ::remaining-rounds (s/coll-of ::round :max-count 2))
(s/def ::players (s/coll-of ::player/id
                            :count 2
                            :distinct true))

(s/def ::game
  (s/keys :req [::players
                ::round
                ::past-rounds
                ::remaining-rounds]))

(defn round
  "Creates a round with the given turn order and deck."
  ([players deck]
   (round players deck 44))
  ([players deck draw-count]
   (let [hands (partition 8 (take 16 deck))
         draw-pile (take draw-count (drop 16 deck))
         initial-player-data (map (fn [hand] {::player/hand hand
                                              ::player/expeditions card/empty-piles}) hands)
         player-data (zipmap players initial-player-data)]
     {::turn (first players)
      ::players players
      ::player-data player-data
      ::discard-piles card/empty-piles
      ::draw-pile draw-pile
      ::moves []})))

(s/fdef round
  :args (s/or :draw-count (s/cat :players ::players
                                 :deck ::card/pile
                                 :draw-count (set (range 0 45)))
              :no-draw-count (s/cat :players ::players
                                 :deck ::card/pile
                                 :draw-count (set (range 0 45))))
  :ret ::round)

(defn rand-round
  "Creates a round with a random turn order and a shuffled deck."
  [players]
  (round (shuffle players) (shuffle card/deck)))

(s/fdef rand-round
  :args (s/cat :players ::players)
  :ret ::round)

(defn validate-placement
  "Validates card placement. Returns nil if valid or a keyword describing the error condition if invalid."
  [round move]
  (let [{:keys [::player/id ::card/card ::destination]} move
        player (get-in round [::player-data id])
        {:keys [::player/hand ::player/expeditions]} player]
    ;; You can't play a card you don't have.
    (if-not (some #{card} hand)
      :card-not-in-hand
      ;; You can discard any card, no matter what.
      (when (= destination :expedition)
        (let [{card-type ::card/type
               card-color ::card/color
               card-number ::card/number} card
              expedition (get expeditions card-color)]
          ;; You can play any card if an expedition hasn't been started yet.
          (when-let [top-card (last expedition)]
            (let [{top-card-type ::card/type
                   top-card-number ::card/number} top-card]
              (case top-card-type
                ;; When the top card is a wager, check wage limit.
                :wager (when (and (= card-type :wager)
                                  (= 3 (count expedition)))
                         :wage-limit)
                :number (case card-type
                          ;; You can't play a wager if a number card has been played.
                          :wager :expedition-underway
                          ;; You can only play cards with an equal or greater value than the top card.
                          :number (when (<= card-number top-card-number)
                                    :too-low))))))))))

(defn validate-draw
  "Validates card draw. Returns nil if valid, keyword error condition if invalid."
  [round move]
  (let [{:keys [::source
                ::destination
                ::card/card]} move
        ;; It's always OK to draw from the draw pile.
        drawing-from-draw-pile? (= source :draw-pile)
        ;; We're OK if we just discarded a card with the same color as
        ;; the discard pile we're drawing from.
        taking-own-discard? (and (= destination :discard-pile)
                                 (= (::card/color card) source))
        discards-available? (not (empty? (get-in round [::discard-piles source])))]
    (when-not (or drawing-from-draw-pile?
                  taking-own-discard?
                  discards-available?)
      :discard-pile-empty)))

(defn validate-move
  "Validates a move. Returns nil if valid, keyword error condition if invalid."
  [round move]
  (if-let [placement-issue (validate-placement round move)]
    placement-issue
    (validate-draw round move)))

(defn drawn-card
  "Returns the card that will be drawn when the given move is executed."
  [round {:keys [::source ::destination ::card/card]}]
  (if (= source :draw-pile)
    (first (::draw-pile round))
    (let [taking-own-discard? (and (= destination :discard-pile)
                                   (= (::card/color card) source))]
      (if taking-own-discard?
        card
        (let [discard-pile (get-in round [::discard-piles source])]
          (last discard-pile))))))

(defn remove-drawn-card
  "Removes the drawn card from the appropriate source."
  [round move drawn-card]
  (let [source (::source move)]
    (if (= source :draw-pile)
      (update round ::draw-pile rest)
      (update-in round [::discard-piles source] #(or (vec (butlast %)) [])))))

(defn remove-from-hand
  "Removes the first instance of card from the given player's hand."
  [round player-id card]
  (update-in round
             [::player-data player-id ::player/hand]
             (fn [hand] (remove #(= % card) hand))))

(defn add-to-hand
  "Adds an instance of card to the given player's hand."
  [round player-id card]
  (update-in round [::player-data player-id ::player/hand] #(conj % card)))

(defn discard-card
  "Places an instance of card into the appropriate discard pile based on card color."
  [round card]
  (update-in round [::discard-piles (::card/color card)] #(conj % card)))

(defn play-card
  "Places an instance of card into the player's appropriate expedition based on card color."
  [round player card]
  (update-in round [::player-data player ::player/expeditions (::card/color card)] #(conj % card)))

(defn make-move
  [round move drawn-card]
  (let [player-id (::player/id move)
        card (::card/card move)
        place-card (case (::destination move)
                     :expedition #(play-card % player-id card)
                     :discard-pile #(discard-card % card))]
    (-> round
        (remove-from-hand player-id card)
        (place-card)
        (remove-drawn-card move drawn-card)
        (update ::moves #(conj % move))
        (add-to-hand player-id drawn-card))))

(s/fdef make-move
  :args (s/cat :round ::round :move ::move :drawn-card ::card/card)
  :ret ::round)

(defn swap-turn
  "Makes it the other player's turn."
  [round players]
  (update round ::turn #(misc/cycle-after players %)))

(s/fdef swap-turn
  :args (s/cat :round ::round :players ::players)
  :ret ::round)

(defn round-over?
  "Returns true if the round is over."
  [round]
  (empty? (::draw-pile round)))

(defn game
  "Creates a game with given turn order and decks."
  ([players decks]
   (game players decks 44))
  ([players [deck-1 deck-2 deck-3] draw-count]
   {::players players
    ::round (round players deck-1 draw-count)
    ::past-rounds []
    ::remaining-rounds [(round (reverse players) deck-2 draw-count)
                        (round players deck-3 draw-count)]}))

(s/fdef game
  :args (s/or :draw-count (s/cat :players ::players
                                 :decks (s/coll-of ::card/pile :count 3)
                                 :draw-count (set (range 0 45)))
              :no-draw-count (s/cat :players ::players
                                    :decks (s/coll-of ::card/pile :count 3)))
  :ret ::game)

(defn rand-game
  "Create a game with a random turn order and shuffled decks."
  ([players]
   (rand-game players 44))
  ([players draw-count]
   (game
    (shuffle players)
    (repeatedly 3 #(shuffle card/deck))
    draw-count)))

(s/fdef rand-game
  :args (s/or :draw-count (s/cat :players ::players
                                 :draw-count (set (range 0 45)))
              :no-draw-count (s/cat :players ::players))
  :ret ::game)

(defn game-over?
  "Returns true if the game is over."
  [{round ::round remaining-rounds ::remaining-rounds}]
  (and (nil? round) (empty? remaining-rounds)))

(defn valid-player?
  "Returns true if the move is being made by a valid player, otherwise false."
  [game move]
  (boolean (some #{(::player/id move)} (::players game))))

(defn right-player?
  "Returns true if the move is being made by the player whose turn it is."
  [game move]
  (let [turn (-> game ::round ::turn)
        player (::player/id move)]
    (= turn player)))

(defn take-turn-no-validation
  [game move drawn-card]
  (let [round (-> game
                  ::round
                  (make-move move drawn-card)
                  (swap-turn (::players game)))]
    (if (round-over? round)
      (let [[new-round & remaining-rounds] (::remaining-rounds game)
            past-rounds (conj (::past-rounds game) round)
            game* (assoc game
                         ::round new-round
                         ::past-rounds past-rounds
                         ::remaining-rounds remaining-rounds)]
        {::status (if new-round :round-over :game-over)
         ::move move
         ::drawn-card drawn-card
         ::game game*})
      {::status :taken
       ::game (assoc game ::round round)
       ::move move
       ::drawn-card drawn-card})))

(defn take-turn
  "Takes a turn."
  [game move]
  (cond
    (game-over? game) {::status :game-over ::game game}
    (not (valid-player? game move)) {::status :invalid-player ::game game}
    (not (right-player? game move)) {::status :wrong-player ::game game}
    :else
    (if-let [move-issue (validate-move (::round game) move)]
      {::status move-issue ::game game}
      (let [drawn-card (drawn-card (::round game) move)]
        (take-turn-no-validation game move drawn-card)))))

(s/def ::response (s/keys :req [::status ::game]))

(s/fdef take-turn
  :args (s/cat :game ::game :move ::move)
  :ret ::response)

(defn opponent
  [game player]
  (first (filter #(not= % player) (::players game))))

(s/fdef opponent
  :args (s/cat :game ::game :player ::player/id)
  :ret ::player/id)

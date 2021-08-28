(ns cities.card
  (:refer-clojure :exclude [number?])
  #?(:clj
     (:require [clojure.spec.alpha :as s]
               [clojure.string :as str]
               [clojure.test.check.generators :as gen])
     :cljs
     (:require [cljs.spec.alpha :as s]
               [clojure.string :as str]
               [clojure.test.check.generators :as gen])))

(def colors #{:blue :green :red :white :yellow})

(s/def ::type #{:wager :number})
(s/def ::color colors)
(s/def ::number pos-int?)

(defn wager? [card] (= (::type card) :wager))
(defn number? [card] (= (::type card) :number))

(defn card
  [type color number]
  {::type type ::color color ::number number})

(defn number
  [color number]
  {::type :number ::color color ::number number})

(defn wager
  [color number]
  {::type :wager ::color color ::number number})

(defn wager-1 [color] (wager color 1))
(defn wager-2 [color] (wager color 2))
(defn wager-3 [color] (wager color 3))

(defn for-color
  [color]
  (set
   (concat
    (map (partial wager color) (range 1 4))
    (map (partial number color) (range 2 11)))))

(def deck (set (reduce concat (map for-color colors))))

(s/def ::card deck)

(s/def ::deck
  (s/with-gen
    (s/coll-of deck :distinct true :count (count deck))
    #(gen/shuffle deck)))

(s/def ::pile (s/coll-of ::card :distinct true))

(s/def ::blue (s/coll-of (for-color :blue) :distinct true))
(s/def ::green (s/coll-of (for-color :green) :distinct true))
(s/def ::red (s/coll-of (for-color :red) :distinct true))
(s/def ::white (s/coll-of (for-color :white) :distinct true))
(s/def ::yellow (s/coll-of (for-color :yellow) :distinct true))

(s/def ::color-piles (s/keys :req-un [::blue ::green ::red ::white ::yellow]))

(def empty-piles (into {} (map #(vector % []) colors)))

(defn combine-piles
  [piles]
  (reduce
   (fn [cards [color pile]]
     (apply conj cards pile))
   []
   (select-keys piles colors)))

(defn label
  "Builds a short string label for a card."
  [{:keys [::color ::type ::number]}]
  (str (name color) "-" (when (= type :wager) (str "wager-")) number))

(defn description
  "Builds an English description of a card."
  [{:keys [::color ::type ::number]}]
  (let [color (-> color name str/capitalize)]
    (if (= type :wager)
      (str "a " color " wager card")
      (str color " " number))))

(defn literal-card
  [{:keys [::color ::type ::number]}]
  (case type
    :number `(card/number ~color ~number)
    :wager `(card/wager ~color ~number)))

(s/fdef wager?
  :args (s/cat :card ::card)
  :ret boolean?)

(s/fdef number?
  :args (s/cat :card ::card)
  :ret boolean?)

(s/fdef combine-piles
  :args (s/cat :piles ::color-piles)
  :ret ::pile)

(s/fdef description
  :args (s/cat :card ::card)
  :ret string?)

(s/fdef literal-card
  :args (s/cat :card ::card)
  :ret list?)

(s/fdef number
  :args (s/cat :type ::type :color ::color :number ::number)
  :ret ::card)

(s/fdef number
  :args (s/cat :color ::color
               :number (s/int-in 2 11))
  :ret (s/and ::card
              number?))

(s/fdef wager
  :args (s/cat :color ::color
               :number (s/int-in 1 4))
  :ret (s/and ::card
              wager?))

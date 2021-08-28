(ns cities.player
  (:require
   #?(:clj [clojure.spec.alpha :as s]
      :cljs [cljs.spec.alpha :as s])
   [cities.card :as card]))

(defn under-wager-limit?
  [cards]
  (<= (count (filter card/wager? cards)) 3))

(s/fdef under-wager-limit?
  :args (s/cat :cards (s/coll-of ::card/card))
  :ret boolean?)

(defn numbers-in-order?
  [cards]
  (let [numbers (into []
                      (comp (filter card/number?)
                            (map ::card/number))
                      cards)]
    (if (empty? numbers)
      true
      (apply <= numbers))))

(s/fdef numbers-in-order?
  :args (s/cat :cards (s/coll-of ::card/card))
  :ret boolean?)

(defn wagers-before-numbers?
  [cards]
  (not
   (loop [[head & tail] cards
          found-number? false]
     (when head
       (let [number? (card/number? head)]
         (if (and (not number?) found-number?)
           true
           (recur tail (or found-number? number?))))))))

(s/fdef wagers-before-numbers?
  :args (s/cat :cards (s/coll-of ::card/card))
  :ret boolean?)

(s/def ::id (s/and string? #(<= 1 (count %) 24)))

(s/def ::blue (s/and ::card/blue
                     wagers-before-numbers?
                     numbers-in-order?))
(s/def ::green (s/and ::card/green
                      wagers-before-numbers?
                      numbers-in-order?))
(s/def ::red (s/and ::card/red
                    wagers-before-numbers?
                    numbers-in-order?))
(s/def ::white (s/and ::card/white
                      wagers-before-numbers?
                      numbers-in-order?))
(s/def ::yellow (s/and ::card/yellow
                       wagers-before-numbers?
                       numbers-in-order?))

(s/def ::expeditions (s/keys :req-un [::blue ::green ::red ::white ::yellow]))
(s/def ::opponent-expeditions (s/keys :req-un [::blue ::green ::red ::white ::yellow]))
(s/def ::hand (s/coll-of ::card/card :count 8))
(s/def ::data (s/keys :req [::hand ::expeditions]))

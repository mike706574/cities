(ns cities.frontend.views.hand
  (:require [re-frame.core :as rf]
            [cities.card :as card]
            [taoensso.timbre :as log]))

(defn rank [card] (if (card/wager? card) "W" (str (:cities.card/number card))))

(defn drawn-card
  [num source]
  (if (= source :draw-pile)
    [:div
     {:key "drawn-card"
      :class (str "card card-highlighted purple hand-" num)}
     [:p "D"]]
    (let [drawn-card @(rf/subscribe [:drawn-discard])
          rank (rank drawn-card)]
      (println "rendering here")
      [:div
       {:key "drawn-card"
        :class (str "card card-highlighted "
                    (name source)
                    " rank-" rank
                    " hand-" num)}
       [:p rank]])))

(defn hand []
  (let [hand @(rf/subscribe [:hand])
        turn? @(rf/subscribe [:turn?])
        selected-card @(rf/subscribe [:card])
        source @(rf/subscribe [:source])
        destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])]
    [:div
     (->> hand
          (map-indexed
           (fn [index card]
             (let [num (inc index)
                   selected? (= card selected-card)]
               (if (and turn? destination selected?)
                 (when source (drawn-card num source))
                 (let [rank (rank card)
                       color (name (:cities.card/color card))
                       classes (str "card "
                                    color
                                    " rank- " rank
                                    " hand-" num
                                    (when turn? " pointer")
                                    (when selected? " selected-card"))]
                   [:div
                    {:key num
                     :class classes
                     :on-click #(when turn?
                                  (rf/dispatch [:card-change card]))}
                    [:p rank]])))))
          doall)]))

(ns cities.frontend.views.expedition
  (:require [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [taoensso.timbre :as log]))

(def collapse (partial mapcat identity))

(defn rank [card] (if (card/wager? card) "W" (str (:cities.card/number card))))

(defn expedition-card
  [card num]
  (let [rank (rank card)
        color (name (:cities.card/color card))
        classes (str "card "
                     color
                     " expedition-" color
                     " rank-" rank
                     " expedition-" num)]
    [:div
     {:key classes
      :class classes}
     [:p rank]]))

(def markers
  [:div.markers
   [:div.expedition-marker.expedition-yellow.light-yellow.expedition-1
    {:key "yellow-opponent-expedition-marker"}]
   [:div.expedition-marker.expedition-yellow.light-yellow.expedition-14
    {:key "yellow-player-expedition-marker"}]
   [:div.expedition-marker.expedition-red.light-red.expedition-1
    {:key "red-opponent-expedition-marker"}]
   [:div.expedition-marker.expedition-red.light-red.expedition-14
    {:key "red-player-expedition-marker"}]
   [:div.expedition-marker.expedition-blue.light-blue.expedition-1
    {:key "blue-opponent-expedition-marker"}]
   [:div.expedition-marker.expedition-blue.light-blue.expedition-14
    {:key "blue-player-expedition-marker"}]
   [:div.expedition-marker.expedition-green.light-green.expedition-1
    {:key "green-opponent-expedition-marker"}]
   [:div.expedition-marker.expedition-green.light-green.expedition-14
    {:key "green-player-expedition-marker"}]
   [:div.expedition-marker.expedition-white.light-white.expedition-1
    {:key "white-opponent-expedition-marker"}]
   [:div.expedition-marker.expedition-white.light-white.expedition-14
    {:key "white-player-expedition-marker"}]])

(defn player-expeditions
  [expeditions]
  [:div
   (map
    #(map-indexed (fn [index card] (expedition-card card (- 14 index))) %)
    (vals expeditions))])

(defn opponent-expeditions
  [expeditions]
  [:div
   (map
    #(map-indexed (fn [index card] (expedition-card card (inc index))) %)
    (vals expeditions))])

(defn selected-cards []
  (let [destination @(rf/subscribe [:destination])
        expeditions @(rf/subscribe [:expeditions])
        card @(rf/subscribe [:card])]
    [:div
     (when (and card (= destination :expedition))
       (let [color (:cities.card/color card)
             num (- 14 (count (get expeditions color)))
             rank (rank card)
             classes (str "card selected-card "
                          (name color)
                          " expedition-" (name color)
                          " rank-" rank
                          " expedition-" num)]
         [:div
          {:key classes
           :class classes}
          [:p rank]]))]))

(defn round-over-expeditions
  [player-exps opponent-exps]
  [:div
   markers
   [player-expeditions player-exps]
   [opponent-expeditions opponent-exps]])

(defn in-game-expeditions []
  (let [turn? @(rf/subscribe [:turn?])]
    [:div
     {:class (str "top-container no-select" (when turn? " pointer"))
      :on-click #(rf/dispatch [:select-destination :expedition])}
     markers
     [player-expeditions @(rf/subscribe [:expeditions])]
     [opponent-expeditions @(rf/subscribe [:opponent-expeditions])]
     [selected-cards]]))

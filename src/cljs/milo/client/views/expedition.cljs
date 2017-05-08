(ns milo.client.views.expedition
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [taoensso.timbre :as log]))

(def collapse (partial mapcat identity))

(defn rank [card] (if (card/wager? card) "W" (str (:milo.card/number card))))

(defn expedition-card
  [card num]
  (let [rank (rank card)
        color (name (:milo.card/color card))
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
  [:div
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

(defn player-expeditions []
  (let [expeditions @(rf/subscribe [:expeditions])]
    [:div
     (map
      #(map-indexed (fn [index card] (expedition-card card (- 14 index))) %)
      (vals expeditions))]))

(defn opponent-expeditions []
  (let [expeditions @(rf/subscribe [:opponent-expeditions])]
    [:div
     (map
      #(map-indexed (fn [index card] (expedition-card card (inc index))) %)
      (vals expeditions))]))

(defn selected-cards []
  (let [destination @(rf/subscribe [:destination])
        expeditions @(rf/subscribe [:expeditions])
        card @(rf/subscribe [:card])]
    [:div
     (when (and card (= destination :expedition))
       (let [color (:milo.card/color card)
             num (- 14 (count (get player-expeditions color)))
             rank (rank card)
             classes (str "card selected-card "
                          (name color)
                          " expedition-" (name color)
                          " rank-" rank
                          " expedition-" num)]
         [[:div
           {:key classes
            :class classes}
           [:p rank]]]))]))

(defn expeditions []
  (log/info "Rendering expeditions.")
  [:div.top-container.mdl-cell.mdl-cell--12-col
   {:on-click #(rf/dispatch [:select-destination :expedition])}
   markers
   [player-expeditions]
   [opponent-expeditions]
   [selected-cards]])

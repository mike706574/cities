(ns cities.frontend.views.game
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :refer [pretty]]
            [cities.frontend.views.expedition :as expedition-view]
            [cities.frontend.views.hand :as hand-view]
            [cities.frontend.views.toast :as toast-view]
            [taoensso.timbre :as log]))

(def app-title "Lost Cities")

(defn rank [card] (if (card/wager? card) "W" (str (:cities.card/number card))))

(defn classes [entries]
  (->> entries
       (filter (fn [entry]
                 (or (string? entry)
                     (= (count entry) 1)
                     (second entry))))
       (map (fn [entry]
              (if (string? entry)
                entry
                (first entry))))))

(defn discards [destination]
  (log/info "Rendering discard piles.")
  (let [destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])
        source @(rf/subscribe [:source])
        turn? @(rf/subscribe [:turn?])
        selected-card @(rf/subscribe [:card])]
    [:div
     (map
      (fn [card]
        (let [color (:cities.card/color card)]
          (when-not (or (= source color) (= destination color))
            (let [rank (rank card)
                  class (classes ["card"
                                  (name color)
                                  (str "rank-" rank)
                                  (str (name color) "-discard")
                                  ["pointer" (and turn? destination)]])]
              [:div
               {:key (str "discard-" (name color))
                :class class
                :on-click #(when destination
                             (rf/dispatch [:source-change color]))}
               [:p rank]]))))
      available-discards)]))

(defn discard []
  (let [destination @(rf/subscribe [:destination])
        selected-card @(rf/subscribe [:card])
        color (:cities.card/color selected-card)
        source @(rf/subscribe [:source])]
    (when (and destination (not= destination :expedition) (not= source color))
      (log/info "Rendering discard.")
      (let [rank (rank selected-card)
            classes (str "card "
                         (name color)
                         " rank-" rank
                         " "
                         (name color) "-discard")]
        [:div
         {:key classes
          :class classes}
         [:p rank]]))))

(defn draw-pile []
  (let [source @(rf/subscribe [:source])
        turn? @(rf/subscribe [:turn?])
        card @(rf/subscribe [:card])
        destination @(rf/subscribe [:destination])]
    [:div.draw-pile
     {:key "draw-pile"
      :on-click #(when card
                   (if-not destination
                     (rf/dispatch [:select-destination :discard-pile])
                     (rf/dispatch [:source-change :draw-pile])))
      :class (str "draw-pile"
                  (when (= source :draw-pile) " selected-card")
                  (when turn? " pointer"))}
     [:p "D"]]))

(defn ready-button
  []
  (when @(rf/subscribe [:turn-ready?])
    [:button
     {:style {"position" "absolute"
              "left" "120px"
              "top" "170px"
              "zIndex" "9"}
      :on-click #(rf/dispatch [:take-turn])}
     "Submit"]))

(defn bottom []
  (log/info "Rendering bottom.")
  (let [destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])
        turn? @(rf/subscribe [:turn?])]
    [:div.bottom-container
     [hand-view/hand]
     [discards]
     [discard]
     [draw-pile]
     [ready-button]]))

(defn button
  [label on-click]
  [:button
   {:value label
    :on-click  on-click}
   label])

(defn inline-cards
  [cards]
  (if (empty? cards)
    [:p.no-spacing "None."]
    [:ul.no-space
     (map-indexed
      (fn [index card]
        [:li
         {:key index
          :style {"display" "inline"}}
         [:span
          {:class (name (::card/color card))}
          (card/label card)]])
      cards)]))

(defn player-info []
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        turn? @(rf/subscribe [:turn?])]
    [:div
     {:style {"margin" "8px 0 0 54px"
              "fontWeight" "500"}}
     (if turn?
      (str "Your turn versus " opponent)
      (str opponent "'s turn"))]))

(defn round-info []
  (let [round-number @(rf/subscribe [:round-number])
        draw-count @(rf/subscribe [:draw-count])]
    [:div
     {:style {"marginLeft" "54px"
              "fontWeight" "500"}}
     (str "Round #" round-number " - " draw-count " cards left")]))

(defn container []
  (log/debug "Rendering game...")
  [:div
   [player-info]
   [round-info]
   [toast-view/toast]
   [expedition-view/in-game-expeditions]
   [bottom]])

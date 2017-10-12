(ns cities.client.views.game
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.client.misc :refer [pretty]]
            [cities.client.views.expedition :as expedition-view]
            [cities.client.views.hand :as hand-view]
            [cities.client.views.toast :as toast-view]
            [taoensso.timbre :as log]))

(def app-title "Lost Cities")

(defn rank [card] (if (card/wager? card) "W" (str (:cities.card/number card))))

(defn discards [destination]
  (log/info "Rendering discard piles.")
  (let [destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])
        source @(rf/subscribe [:source])
        selected-card @(rf/subscribe [:card])]
    [:div
     (map
      (fn [card]
        (let [color (:cities.card/color card)]
          (when-not (or (= source color) (= destination color))
            (let [rank (rank card)
                  classes (str "card "
                               (name color)
                               " rank-" rank
                               " "
                               (name color) "-discard")]
              [:div
               {:key classes
                :class classes
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
                     (rf/dispatch [:select-destination (:cities.card/color card)])
                     (rf/dispatch [:source-change :draw-pile])))
      :class (str "draw-pile"
                  (when (= source :draw-pile) " selected-card")
                  (when turn? " pointer"))}
     [:p "D"]]))

(defn ready-button
  []
  (when @(rf/subscribe [:turn-ready?])
    [:button.mdl-button.mdl-js-button.mdl-button--fab.mdl-button--colored
     {:style {"position" "absolute"
              "left" "120px"
              "top" "100px"
              "zIndex" "9"
              "border" "1px solid white"}
      :on-click #(rf/dispatch [:take-turn])}
     [:i.material-icons "check"]]))

(defn bottom []
  (log/info "Rendering bottom.")
  (let [destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])
        turn? @(rf/subscribe [:turn?])]
    [:div.bottom-container.mdl-cell.mdl-cell--12-col
     [hand-view/hand]
     [discards]
     [discard]
     [draw-pile]
     [ready-button]]))

(defn button
  [label on-click]
  [:button.mdl-button.mdl-button--raised
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
   [:div.mdl-demo-navigation
    [mdl/layout
     :fixed-header? true
     :children
     [[mdl/layout-header
       :attr {:style {:color "white"}}
       :children
       [[:button.mdl-layout-icon.mdl-button.mdl-js-button.mdl-button--icon
         {:on-click #(rf/dispatch [:show-menu])}
         [:i.material-icons "arrow_back"]]
        [player-info]
        [round-info]]]
      [mdl/layout-content
       :attr {:style {:background "white"}}
       :children [[expedition-view/in-game-expeditions]
                  [bottom]
                  [toast-view/toast]]]]]]])

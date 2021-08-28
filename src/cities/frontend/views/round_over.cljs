(ns cities.frontend.views.round-over
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :as misc :refer [pretty]]
            [cities.frontend.views.expedition :as expedition-view]
            [cities.frontend.views.hand :as hand-view]
            [cities.frontend.views.toast :as toast-view]
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

(defn button
  [label on-click]
  [:button.mdl-button.mdl-button--raised
   {:value label
    :on-click  on-click}
   label])

(defn player-info []
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        turn? @(rf/subscribe [:turn?])]
    [:div
     {:style {"margin" "8px 0 0 54px"
              "fontWeight" "500"}}
     "Versus " opponent]))

(defn round-info []
  (let [round-number @(rf/subscribe [:round-number])
        draw-count @(rf/subscribe [:draw-count])]
    [:div
     {:style {"marginLeft" "54px"
              "fontWeight" "500"}}
     (str "Round Over")]))

(defn content []
  (let [last-round @(rf/subscribe [:last-round])
        player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        all-data (:cities.game/player-data last-round)
        player-expeditions (get-in all-data [player :cities.player/expeditions])
        opponent-expeditions (get-in all-data [opponent :cities.player/expeditions])
        opponent-scores (misc/map-vals score/expedition-score opponent-expeditions)
        player-scores (misc/map-vals score/expedition-score player-expeditions)
        opponent-sum (reduce + (vals opponent-scores))
        player-sum (reduce + (vals player-scores))]
    [:div
     [:p {:style {"textAlign" "center"
                  "marginBottom" "0"}}
      opponent-sum]
     (let [{:keys [red green blue yellow white]} opponent-scores]
       [:div.score-container
        [:div.score.score-red.red-text red]
        [:div.score.score-green.green-text green]
        [:div.score.score-blue.blue-text blue]
        [:div.score.score-yellow.yellow-text yellow]
        [:div.score.score-white.white-text white]])
     [expedition-view/round-over-expeditions
      player-expeditions
      opponent-expeditions]
     (let [{:keys [red green blue yellow white]} player-scores]
       [:div.score-container
        [:div.score.score-red.red-text red]
        [:div.score.score-green.green-text green]
        [:div.score.score-blue.blue-text blue]
        [:div.score.score-yellow.yellow-text yellow]
        [:div.score.score-white.white-text white]])
     [:p {:style {"textAlign" "center"
                  "marginBottom" "0"}}
      player-sum]
     [:div.mdl-grid
      [:div.play-container
       [:button.play.mdl-button.mdl-button--raised
        {:value "Play"
         :on-click #(rf/dispatch [:back-to-game])}
        "Play"]]]]))

(defn container []
  (log/debug "Rendering game...")
  [:div
   [content]])

(ns milo.client.views.game
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [milo.client.misc :refer [pretty]]
            [milo.client.views.expedition :as expedition-view]
            [milo.client.views.hand :as hand-view]
            [milo.client.views.toast :as toast-view]
            [taoensso.timbre :as log]))

(def app-title "Misplaced Villages")

(defn rank [card] (if (card/wager? card) "W" (str (:milo.card/number card))))

(defn hand []
  (let [hand @(rf/subscribe [:hand])
        turn? @(rf/subscribe [:turn?])
        selected-card @(rf/subscribe [:card])
        source @(rf/subscribe [:source])
        destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])]
    [:div
     (map-indexed
      (fn [index card]
        (let [num (inc index)]
          (if (and destination (= card selected-card))
            (when source
              (if (= source :draw-pile)
                [:div
                 {:key "drawn-card"
                  :class (str "card card-highlighted purple hand-" num)}
                 [:p "D"]]
                (let [drawn-card @(rf/subscribe [:drawn-discard])
                      rank (rank drawn-card)]
                  [:div
                   {:key "drawn-card"
                    :class (str "card card-highlighted " (name source) " rank-" rank " hand-" num)}
                   [:p rank]])))
            (let [rank (rank card)
                  color (name (:milo.card/color card))
                  classes (str "card "
                               color
                               " rank- " rank
                               " hand-" num
                               (when (= selected-card card) " selected-card"))]
              [:div
               {:key num
                :class classes
                :on-click #(when turn?
                             (rf/dispatch [:card-change card]))}
               [:p rank]]))))
      hand)]))

(defn discards []
  (let [available-discards @(rf/subscribe [:available-discards])
        source @(rf/subscribe [:source])
        destination @(rf/subscribe [:destination])]
    [:div
     (map
      (fn [card]
        (let [color (:milo.card/color card)]
          (when-not (= source color)
            (let [rank (rank card)
                  classes ["card"
                           (name color)
                           (str "rank-" rank)
                           (str (name color) "-discard")
                           (when (= source color) "selected-card")]]
              [:div
               {:key classes
                :class (str/join " " classes)
                :on-click #(when destination
                             (rf/dispatch [:source-change color]))}
               [:p rank]]))))
      available-discards)]))

(defn draw-pile []
  (let [source @(rf/subscribe [:source])]
    [:div.draw-pile
     {:key "draw-pile"
      :on-click #(rf/dispatch [:source-change :draw-pile])
      :class (str "draw-pile" (when (= source :draw-pile) " selected-card"))}
     [:p "D"]]))

(defn bottom []
  (log/info "Rendering hand.")
  (let [destination @(rf/subscribe [:destination])
        available-discards @(rf/subscribe [:available-discards])

        turn? @(rf/subscribe [:turn?])]
    [:div.mdl-grid
     [:div.bottom-container.mdl-cell.mdl-cell--12-col
      [hand]
      [discards]
      [draw-pile]]]))

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

(defn destination-view []
  (let [destination @(rf/subscribe [:destination])]
    [:ul.no-space
     [:li {:key :play
           :style {"display" "inline"}}
      [:input {:type "radio"
               :checked (= destination :expedition)
               :on-change #(rf/dispatch [:destination-change :expedition])}]
      [:span "expedition"]]
     [:li {:key :discard
           :style {"display" "inline"}}
      [:input {:type "radio"
               :checked (= destination :discard-pile)
               :on-change #(rf/dispatch [:destination-change :discard-pile])}]
      [:span.black "discard"]]]))

(defn source-view
  [available-discards]
  (let [source @(rf/subscribe [:source])]
    [:ul.no-space
     [:li {:key :draw-pile
           :style {"display" "inline"}}
      [:input {:type "radio"
               :checked (= source :draw-pile)
               :on-change #(rf/dispatch [:source-change :draw-pile])}]
      [:span.black "draw"]]
     (map
      (fn [card]
        (let [color (::card/color card)]
          [:li
           {:key color
            :style {"display" "inline"}}
           [:input {:type "radio"
                    :checked (= source color)
                    :on-change #(rf/dispatch [:source-change color])}]
           [:span
            {:class (name color)}
            (card/label card)]]))
      available-discards)]))

(defn play-button
  []
  (when @(rf/subscribe [:turn-ready?])
    [:div
     (button (str "Take Turn") #(rf/dispatch [:take-turn]))
     (when-let [move-message @(rf/subscribe [:move-message])]
       [:p.red-text move-message])]))

(defn game []
  (log/info "Rendering game.")
  [:div
   {:style {"paddingTop" "5px"}}
   [expedition-view/expeditions]
   [bottom]
   [play-button]])

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
  (log/debug "Rendering game container...")
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
       :children [[game]
                  [toast-view/toast]]]]]]])

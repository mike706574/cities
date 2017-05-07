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

(defn game []
  (log/info "Rendering game.")
  (let [available-discards @(rf/subscribe [:available-discards])
        move-message @(rf/subscribe [:move-message])
        turn? @(rf/subscribe [:turn?])]
    [:div
     {:style {"paddingTop" "5px"}}
     [expedition-view/expedition]
     [hand-view/active]
     (if turn?
       [:div
        (button (str "Take Turn") #(rf/dispatch [:take-turn]))
        (when move-message [:p.red-text move-message])])]))

(defn player-info []
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        turn? @(rf/subscribe [:turn?])]
    [:div
     {:style {"marginTop" "8px"
              "fontWeight" "500"}}
     (if turn?
      (str "Your turn versus " opponent)
      (str opponent "'s turn"))]))

(defn round-info []
  (let [round-number @(rf/subscribe [:round-number])
        draw-count @(rf/subscribe [:draw-count])]
    [:div
     {:style {"fontWeight" "500"}}
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

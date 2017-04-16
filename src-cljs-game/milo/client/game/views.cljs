(ns milo.client.game.views
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [taoensso.timbre :as log]))

(defn button
  [label on-click]
  [:input.btn.btn-default
   {:type "button"
    :value label
    :on-click  on-click}])

(defn player-selection
  []
  (let [player @(rf/subscribe [:player])]
    [:div
     [button "abby" #(rf/dispatch [:initialize "abby" "1"])]
     [button "mike" #(rf/dispatch [:initialize "mike" "1"])]]))

(defn active-hand-view
  [hand]
  (let [selected-card @(rf/subscribe [:card])]
    [:ul.no-space
     (map-indexed
      (fn [index card]
        [:li
         {:key index
          :style {"display" "inline"}}
         [:input {:type "radio"
                  :checked (= selected-card card)
                  :on-change #(rf/dispatch [:card-change card])}]
         [:span
          {:class (name (::card/color card))}
          (card/str-card card)]])
      hand)]))


(defn inline-cards
  [hand]
  [:ul.no-space
   (map-indexed
    (fn [index card]
      [:li
       {:key index
        :style {"display" "inline"}}
       [:span
        {:class (name (::card/color card))}
        (card/str-card card)]])
    hand)])

(defn stack-table
  [stacks show-scores?]
  (letfn [(th [[color _]]
            [:th {:key color :class (name color)} (name color)])
          (tf [[color stack]]
            [:td {:key color :class (name color)}
             (score/expedition-score stack)])
          (td [row [color cards]]
            (if-let [card (get cards row)]
              [:td {:key color
                    :class (name color)} (card/str-card card)]
              [:td {:key color} ""]))
          (tr [row]
            [:tr
             {:key row}
             (map (partial td row) stacks)])]
    (let [max-count (->> (map (comp count val) stacks)
                         (apply max))
          keys (keys stacks)]
      [:table
       [:thead [:tr (map th stacks)]]
       (when show-scores? [:tfoot [:tr (map tf stacks)] ])
       [:tbody (map tr (range 0 max-count))]])))

(defn expedition-table
  [expeditions]
  (stack-table expeditions false))

(defn expedition-score-table
  [expeditions]
  (stack-table expeditions true))

(defn destination-view
  []
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
            (card/str-card card)]]))
      available-discards)]))

(defn game
  []
  (log/debug "Rendering game!")
  (let [player @(rf/subscribe [:player])
        turn @(rf/subscribe [:turn])
        draw-count @(rf/subscribe [:draw-count])
        round-number @(rf/subscribe [:round-number])
        opponent @(rf/subscribe [:opponent])
        turn? (= player turn)]
    [:div
     [:h3 "Game"]
     [:h4 (str "Round #" round-number)]
     [:p (str  "It's " (if turn? "your" (str opponent "'s")) " turn. "
               "There are " draw-count " cards left in the draw pile.")]
     (if turn?
       [:div
        [:h5 "Hand"]
        (active-hand-view @(rf/subscribe [:hand]))
        [:h5 "Destination"]
        [destination-view]
        [:h5 "Sources"]
        [source-view @(rf/subscribe [:available-discards])]
        (button (str "Take Turn") #(rf/dispatch [:take-turn]))
        (when-let [move-message @(rf/subscribe [:move-message])]
          [:p.red-text move-message])]
       [:div
        [:h5 "Hand"]
        (inline-cards @(rf/subscribe [:hand]))
        [:h5 "Discards"]
        (inline-cards @(rf/subscribe [:available-discards]))])
     [:h5 "Your Expeditions"]
     (expedition-table @(rf/subscribe [:expeditions]))
     [:h5 (str opponent "'s Expeditions")]
     (expedition-table @(rf/subscribe [:opponent-expeditions]))]))

(defn game-over
  []
  (log/debug "Rendering game over screen!")
  (let [player @(rf/subscribe [:player])
        loading? @(rf/subscribe [:loading?])
        opponent @(rf/subscribe [:opponent])
        past-rounds @(rf/subscribe [:past-rounds])
        round-scores (map score/round-scores past-rounds)
        player-score (reduce + (map #(get % player) round-scores))
        opponent-score (reduce + (map #(get % opponent) round-scores))]
    (letfn [(round-summary [number round]
              (let [all-data (::game/player-data round)
                    player-expeditions (get-in all-data [player ::player/expeditions])
                    opponent-expeditions (get-in all-data [opponent ::player/expeditions])
                    number (inc number)]
                [:li
                 {:key number}
                 [:h3 "Round " number]
                 [:h5 "Your expeditions"]
                 [expedition-score-table player-expeditions]
                 [:h5 opponent "'s expeditions"]
                 [expedition-score-table opponent-expeditions]]))]
      [:div
       [:h3 "Game Over"]
       [:table
        [:thead [:tr [:th player] [:th opponent]]]
        [:tfoot [:tr [:td player-score] [:td opponent-score]]]
        [:tbody (map-indexed
                 (fn [index round]
                   [:tr
                    {:key index}
                    [:td (get round player)]
                    [:td (get round opponent)]])
                 round-scores)]]
       [:ul (map-indexed round-summary past-rounds)]])))

(defn splash
  []
  [:p @(rf/subscribe [:status-message])])

(defn error
  []
  [:div
   [:h1 "Error!"]
   [:p @(rf/subscribe [:error-message])]])

(defn app
  []
  (let [screen @(rf/subscribe [:screen])]
    (if (= screen :splash)
      [splash]
      [:div
       [:nav
        [:a {:href "/"} "Menu"]
        " "
        [:a {:href "/logout"} "Log out"]]
       [:p (str "Logged in as " @(rf/subscribe [:player]))]
       (case screen
         :player-selection [player-selection]
         :game [game]
         :game-over [game-over]
         :error [error]
         (throw (js/Error. (str "Invalid screen: " screen))))])))

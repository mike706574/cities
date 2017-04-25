(ns milo.client.views
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [taoensso.timbre :as log]))

(defn button
  [label on-click]
  [:button.mdl-button.mdl-button--raised
   {:value label
    :on-click  on-click}
   label])

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
                    :class (name color)}
               (card/str-card card)]
              [:td {:key color} ""]))
          (tr [row]
            [:tr {:key row}
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
        round-number @(rf/subscribe [:round-number])
        draw-count @(rf/subscribe [:draw-count])
        opponent @(rf/subscribe [:opponent])
        available-discards @(rf/subscribe [:available-discards])
        hand @(rf/subscribe [:hand])
        move-message @(rf/subscribe [:move-message])
        expeditions @(rf/subscribe [:expeditions])
        opponent-expeditions @(rf/subscribe [:opponent-expeditions])
        turn? (= player turn)]
    [:div
     [:h3 (str "Round " round-number " versus " opponent)]
     [:p (str "There are " draw-count " cards left. It's " (if turn? "your" (str opponent "'s")) " turn. ")]
     (if turn?
       [:div
        [:h5 "Hand"]
        (active-hand-view hand)
        [:h5 "Destination"]
        [destination-view]
        [:h5 "Sources"]
        [source-view available-discards]
        (button (str "Take Turn") #(rf/dispatch [:take-turn]))
        (when move-message [:p.red-text move-message])]
       [:div
        [:h5 "Hand"]
        (inline-cards hand)
        [:h5 "Discards"]
        (inline-cards available-discards)])
     [:h5 "Your Expeditions"]
     (expedition-table expeditions)
     [:h5 (str opponent "'s Expeditions")]
     (expedition-table opponent-expeditions)]))

(defn expedition-score-tables
  [round player opponent]
  (let [all-data (::game/player-data round)
        player-expeditions (get-in all-data [player ::player/expeditions])
        opponent-expeditions (get-in all-data [opponent ::player/expeditions])]
    [:div
     [:h5 "Your expeditions"]
     [expedition-score-table player-expeditions]
     [:h5 opponent "'s expeditions"]
     [expedition-score-table opponent-expeditions]]))

(defn game-over
  []
  (log/debug "Rendering game over screen!")
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        past-rounds @(rf/subscribe [:past-rounds])
        round-scores (map score/round-scores past-rounds)
        player-score (reduce + (map #(get % player) round-scores))
        opponent-score (reduce + (map #(get % opponent) round-scores))]
    [:div
     [:h3 "Game Over"]
     [:table
      [:thead [:tr [:th player] [:th opponent]]]
      [:tfoot [:tr [:td player-score] [:td opponent-score]]]
      [:tbody (map-indexed
               (fn [index round]
                 [:tr {:key index}
                  [:td (get round player)]
                  [:td (get round opponent)]])
               round-scores)]]
     [:ul
      (map-indexed
       (fn [index round]
         [:li {:key index}
          (str "Round #" (inc index))
          (expedition-score-tables round player opponent)])
       past-rounds)]]))

(defn round-over
  []
  (log/debug "Rendering round over screen!")
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        round @(rf/subscribe [:last-round])]
    [:div
     [:h3 "Round Over"]
     [button "Play" #(rf/dispatch [:round-screen])]
     (expedition-score-tables round player opponent)]))

(defn splash
  []
  [:p @(rf/subscribe [:status-message])])

(defn sent-invites
  []
  (let [invites @(rf/subscribe [:sent-invites])]
    (letfn [(list-item [index invite]
              [:li.mdl-list__item {:key index}
               [:span.mdl-list__item-primary-content
                (str "You invited " (second invite) " to play.")]
               [:span.mdl-list__item-secondary-content
                (button "Cancel" #(rf/dispatch [:cancel-invite (second invite)]))]])]
      [:div
       [:h5 "Sent Invites"]
       (if (empty? invites)
         [:span "No invites sent."]
         [:ul.demo-list-item.mdl-list
          (map-indexed list-item invites)])])))

(defn received-invites
  []
  (let [invites @(rf/subscribe [:received-invites])]
    (letfn [(list-item [index invite]
              [:li.mdl-list__item {:key index}
               [:span.mdl-list__item-primary-content
                (str (first invite) " invited you to play.")]
               [:span.mdl-list__item-secondary-content
                (button "Accept" #(rf/dispatch [:accept-invite (first invite)]))]
               [:span.mdl-list__item-secondary-content
                (button "Reject" #(rf/dispatch [:reject-invite (first invite)]))]])]
      [:div
       [:h5 "Received Invites"]
       (if (empty? invites)
         [:p "You have no invites."]
         [:ul.demo-list-item.mdl-list
          (map-indexed list-item invites)])])))

(defn game-list
  [games]
  (letfn [(game-item [[id game]]
            (let [{:keys [::game/opponent]} game]
              [:li.mdl-list__item
               {:key id}
               [:span.mdl-list__item-primary-content
                [:i.material-icons.mdl-list__item-avatar "person"]
                opponent]
               [:span.mdl-list__item-secondary-action
                (button "Play" #(rf/dispatch [:play-game id]))]]))]
    [:ul.demo-list-control.mdl-list
     (map game-item games)]))

(defn ready-games []
  (let [games @(rf/subscribe [:ready-games])]
    (when-not (empty? games)
      [:div
       [:h5 "Your turn"]
       (game-list games)])))

(defn waiting-games []
  (let [games @(rf/subscribe [:waiting-games])]
    (when-not (empty? games)
      [:div
       [:h5 "Their turn"]
       (game-list games)])))

(defn menu
  []
  (let [player @(rf/subscribe [:player])
        other-player (case player
                       "mike" "abby"
                       "abby" "mike")]
    (log/debug "Rendering menu.")
      [:div
       [ready-games]
       [waiting-games]
       [received-invites]
       [button (str "Invite " other-player " to play") #(rf/dispatch [:send-invite other-player])]
       [sent-invites]
       [:h5 "Messages"]
       [:ul
        (map-indexed
         (fn [index message]
           [:li {:key index} message])
         @(rf/subscribe [:messages]))]]))

(defn error
  []
  [:div
   [:h5 "Error!"]
   [:p (str @(rf/subscribe [:error-message]))]])

(defn app
  []
  (let [screen @(rf/subscribe [:screen])]
    (case screen
      :splash [splash]
      :game [game]
      :round-over [round-over]
      :game-over [game-over]
      :menu [menu]
      :error [error]
      (throw (js/Error. (str "Invalid screen: " screen))))))

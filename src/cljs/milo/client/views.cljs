(ns milo.client.views
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [milo.client.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(def app-title "Misplaced Villages")

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
          (card/label card)]])
      hand)]))

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
               (card/label card)]
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
     [:h5.no-spacing (str "Round " round-number " versus " opponent)]
     [:p.spaced-text (str "There are " draw-count " cards left. It's " (if turn? "your" (str opponent "'s")) " turn. ")]
     (if turn?
       [:div
        [:h5.no-spacing "Hand"]
        (active-hand-view hand)
        [:h5.no-spacing "Destination"]
        [destination-view]
        [:h5.no-spacing "Sources"]
        [source-view available-discards]
        (button (str "Take Turn") #(rf/dispatch [:take-turn]))
        (when move-message [:p.red-text move-message])]
       [:div
        [:h5.no-spacing "Hand"]
        (inline-cards hand)
        [:h5.no-spacing "Discards"]
        (inline-cards available-discards)])
     [:h5.no-spacing "Your Expeditions"]
     (expedition-table expeditions)
     [:h5.no-spacing (str opponent "'s Expeditions")]
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

(defn game-over []
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

(defn round-over []
  (log/debug "Rendering round over screen!")
  (let [player @(rf/subscribe [:player])
        opponent @(rf/subscribe [:opponent])
        round @(rf/subscribe [:last-round])]
    [:div
     [:h3 "Round Over"]
     [button "Play" #(rf/dispatch [:back-to-game])]
     (expedition-score-tables round player opponent)]))

(defn received-invites []
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
       [:h5.no-margin "Received Invites"]
       (if (empty? invites)
         [:p.spaced-text "You haven't received any invites."]
         [:ul.mdl-list.no-spacing
          (map-indexed list-item invites)])])))

(defn game-list
  [games]
  (letfn [(game-item [[id game]]
            (let [{:keys [::game/opponent]} game]
              [:li.mdl-list__item
               {:key id}
               [:span.mdl-list__item-primary-content
                [:i.material-icons.mdl-list__item-avatar "person"]
                (str opponent " [" id "]")]
               [:span.mdl-list__item-secondary-action
                (button "Play" #(rf/dispatch [:play-game id]))]]))]
    [:ul.mdl-list.no-spacing
     (map game-item games)]))

(defn ready-games []
  (let [games @(rf/subscribe [:ready-games])]
    [:div
     [:h5.no-margin "Your turn"]
     (if (empty? games)
       [:p.spaced-text "No games found."]
       (game-list games))]))

(defn waiting-games []
  (let [games @(rf/subscribe [:waiting-games])]
    [:div
     [:h5.no-margin"Their turn"]
     (if (empty? games)
       [:p.spaced-text"No games found."]
       (game-list games))]))

(defn toast [key]
  (let [{:keys [message action-event action-label color :as toast]}  @(rf/subscribe [key])]
    (log/debug (str "Displaying toast: " (pretty toast)))
    (if toast
      [:div#toast.mdl-js-snackbar.mdl-snackbar.mdl-snackbar--active
       {:aria-hidden "false"}
       [:div.mdl-snackbar__text (or message "No message.")]
       (when action-event
         [:button.mdl-snackbar__action
          {:type "button"
           :on-click #(rf/dispatch action-event)}
          (or action-label "Perform Action")])]
      [:div#toast.mdl-js-snackbar.mdl-snackbar
       {:class ""
        :aria-hidden "true"}
       [:div.mdl-snackbar__text ""]])))

(defn sent-invites []
  (let [invites @(rf/subscribe [:sent-invites])]
    (letfn [(list-item [index invite]
              [:li.mdl-list__item {:key index}
               [:span.mdl-list__item-primary-content
                (str "You invited " (second invite) " to play.")]
               [:span.mdl-list__item-secondary-content
                (button "Cancel" #(rf/dispatch [:cancel-invite (second invite)]))]])]
      (if (empty? invites)
        [:p.spaced-text "You haven't sent any invites."]
        [:ul.mdl-list.no-spacing
         (map-indexed list-item invites)]))))

(defn invite-form []
  (let [recipient-model (r/atom "")
        res "^([a-zA-Z0-9_-])+$"
        re (re-pattern res)]
    (fn []
      (let [player @(rf/subscribe [:player])
            recipient @recipient-model
            valid? (and (re-find re recipient)
                        (not= player recipient))
            send-invite #(when valid?
                           (reset! recipient-model "")
                           (rf/dispatch [:send-invite recipient]))]
        [:div
         [mdl/textfield
          :floating-label? true
          :label "Player"
          :model recipient
          :pattern res
          :invalid-message "Invalid!"
          :handler-fn #(reset! recipient-model %)
          :input-attr {:maxLength 33
                       :on-key-press #(when (= (.-charCode %) 13)
                                        (send-invite))}]
         [mdl/button
          :child "Clear"
          :secondary? true
          :raised? true
          :ripple-effect? true
          :disabled? (str/blank? recipient)
          :attr {:on-click #(reset! recipient-model "")
                 :style {"marginLeft" "1em"}}]
         [mdl/button
          :child "Send Invite"
          :colored? true
          :raised? true
          :ripple-effect? true
          :disabled? (not valid?)
          :attr {:on-click send-invite
                 :style {"marginLeft" "1em"}}]]))))

(defn outbox
  []
  [:div
   [:h5.no-margin "Sent invites"]
   [invite-form]
   [sent-invites]])

(defn error []
  (let [{:keys [error-message error-body]} @(rf/subscribe [:error])]
  [:div
   [:h5 "Error!"]
   [:p error-message]
   [:textarea {:rows "10" :cols "100"} error-body]]))

(defn menu []
  (log/debug "Rendering menu.")
  [:div
   [ready-games]
   [waiting-games]
   [received-invites]
   [outbox]])

(defn container
  [body]
  [:div
   [:div.mdl-demo-navigation
    [mdl/layout
     :fixed-header? true
     :children
     [[mdl/layout-header
       :attr {:style {:color "white"}}
       :children
       [[mdl/layout-header-row
         :children
         [[mdl/layout-title
           :children [[:a {:style {"cursor" "pointer"
                                   "color" "white"}
                           :on-click #(rf/dispatch [:show-menu])}
                       app-title]]]]]]]
      [mdl/layout-drawer
       :children
       [[mdl/layout-title
         :label app-title
         :attr {:on-click #(rf/dispatch [:show-menu])}]]]
      [mdl/layout-content
       :attr {:style {:background "white"}}
       :children [[:div.mdl-layout__conent
                   [:div.mdl-grid.demo-content
                    [:div.mdl-cell.mdl-cell--12-col body]]]
                  [toast :toast]]]]]]])

(defn app []
  (let [screen @(rf/subscribe [:screen])]
    [container
     [(case screen
        :game game
        :round-over round-over
        :game-over game-over
        :menu menu
        :error error
        (throw (js/Error. (str "Invalid screen: " screen))))]]))

(defn initialization-error
  [body]
  [:div
   [:h5 "Initialization error!"]
   [:textarea
    {:rows "100"
     :cols "100"
     :value (pretty body)}]])

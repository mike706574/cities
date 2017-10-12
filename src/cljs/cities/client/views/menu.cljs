(ns cities.client.views.menu
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-mdl.core :as mdl]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.client.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(defn button
  [label on-click]
  [:button.mdl-button.mdl-button--raised
   {:value label
    :on-click  on-click}
   label])

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
                [:img.mdl-list__item-avatar
                 {:src (str "images/" @(rf/subscribe [:avatar opponent])) }]
                (str opponent " [" id "]")]
               [:span.mdl-list__item-secondary-action
                (button "Play" #(rf/dispatch [:play-game id]))]]))]
    [:ul.mdl-list.no-spacing
     (doall (map game-item games))]))

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

(defn menu []
  (log/debug "Rendering menu.")
  [:div.mdl-layout__content
   [:div.mdl-grid
    [:div.mdl-cell.mdl-cell--12-col
     [ready-games]
     [waiting-games]
     [received-invites]
     [outbox]]]])

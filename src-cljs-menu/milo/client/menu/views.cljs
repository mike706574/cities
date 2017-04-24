(ns milo.client.menu.views
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]))

(defn button
  [label on-click]
                  [:a]
  [:button.mdl-button.mdl-button--raised
   {:value label
    :on-click  on-click}
   label])

(defn player-selection
  []
  (let [player @(rf/subscribe [:player])]
    [:div
     [button "abby" #(rf/dispatch [:initialize "abby"])]
     [button "mike" #(rf/dispatch [:initialize "mike"])]]))

(defn splash
  []
  [:h5 @(rf/subscribe [:status-message])])

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
                (button "ACcept" #(rf/dispatch [:accept-invite (first invite)]))]
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
                [:a.mdl-button.mdl-button--raised
                 {:href (str "/game/" id)}
                 "Play"]]]))]
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
      :player-selection [player-selection]
      :menu [menu]
      :error [error]
      (throw (js/Error. (str "Invalid screen: " screen))))))

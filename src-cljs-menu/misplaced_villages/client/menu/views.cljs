(ns misplaced-villages.client.menu.views
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [misplaced-villages.game :as game]
            [misplaced-villages.card :as card]
            [misplaced-villages.player :as player]
            [misplaced-villages.score :as score]))

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
     [button "abby" #(rf/dispatch [:initialize "abby"])]
     [button "mike" #(rf/dispatch [:initialize "mike"])]]))

(defn splash
  []
  [:p @(rf/subscribe [:status-message])])

(defn headed-list
  [label f coll]
  (letfn [(li [index item]
            [:li {:key index} (f item)])]
    [:div
     [:h3 (str/capitalize label)]
     (if (empty? coll)
       [:span "No " label "."]
       [:ul (map-indexed li coll)])]))

(defn menu
  []
  (let [player @(rf/subscribe [:player])
        other-player (case player
                       "mike" "abby"
                       "abby" "mike")]
      [:div
       (button "Sync" #(rf/dispatch [:sync]))
       [:p (str "You are " player ".")]
       [:h3 "Games"]
       [button (str "Invite " other-player) #(rf/dispatch [:send-invite other-player])]
       (let [games @(rf/subscribe [:games])]
         (if (empty? games)
           [:span "You have no games."]
           [:ul
            (for [[id state] games]
              [:li {:key id} [:a {:href (str "/game/" id)} id ", Versus " (::game/opponent state)]])]))
       (headed-list
        "Invites"
        (fn [[_ opponent :as request]]
          [:div
           (str request)
           (button "Cancel" #(rf/dispatch [:cancel-invite opponent]))])
        @(rf/subscribe [:sent-invites]))
       (headed-list
        "Requests"
        (fn [[opponent :as request]]
          [:div
           (str request)
           (button "Accept" #(rf/dispatch [:accept-invite opponent]))
           (button "Reject" #(rf/dispatch [:reject-invite opponent]))])
        @(rf/subscribe [:received-invites]))
       (headed-list "messages" str @(rf/subscribe [:messages]))]))

(defn error
  []
  [:div
   [:h1 "Error!"]
   [:p @(rf/subscribe [:error-message])]])

(defn app
  []
  (let [screen @(rf/subscribe [:screen])]
    [:div
     [:nav
      [:a {:href "/logout"} "Logout"]]
     (case screen
       :splash [splash]
       :player-selection [player-selection]
       :menu [menu]
       :error [error]
       (throw (js/Error. (str "Invalid screen: " screen))))]))

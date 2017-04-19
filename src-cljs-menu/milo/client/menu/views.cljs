(ns milo.client.menu.views
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]))

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

(defn game-item
  [game player]
  (let [{:keys [::game/id ::game/opponent ::game/turn]} game]
    [:div
     [:span
      [:em opponent]
      " "
      [:a {:href (str "/game/" id)} "Go"]]]))

(defn active-game-list
  [games player]
  (if (empty? games)
    [:p "No games!"]
    [:ul
     (for [[id game] games]
       [:li {:key id} (game-item game player)])]))

(defn ready-games
  []
  [:div
   [:h3 "Your Turn"]
   (active-game-list @(rf/subscribe [:ready-games])
                     @(rf/subscribe [:player]))])

(defn waiting-games
  []
  [:div
   [:h3 "Their Turn"]
   (active-game-list @(rf/subscribe [:waiting-games])
                     @(rf/subscribe [:player]))])

(defn menu
  []
  (let [player @(rf/subscribe [:player])
        other-player (case player "mike" "abby" "abby" "mike")]
      [:div
       [ready-games]
       [waiting-games]
       [button (str "Invite " other-player) #(rf/dispatch [:send-invite other-player])]
       (headed-list
        "Invites"
        (fn [[_ opponent :as request]]
          [:div
           (str "You invited " (second request) " to play. ")
           (button "Cancel" #(rf/dispatch [:cancel-invite opponent]))])
        @(rf/subscribe [:sent-invites]))
       (headed-list
        "Requests"
        (fn [[opponent :as request]]
          [:div
           (str (first request) " invited you to play. ")
           (button "Accept" #(rf/dispatch [:accept-invite opponent]))
           " "
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
    (if (= screen :splash)
      [splash]
      [:div
       [:nav
        [:a {:href "/logout"} "Log out"]]
       [:p (str "Logged in as " @(rf/subscribe [:player]))]
       (case screen
         :player-selection [player-selection]
         :menu [menu]
         :error [error]
         (throw (js/Error. (str "Invalid screen: " screen))))])))

(ns misplaced-villages-client.menu.views
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
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
     [button "Abby" #(rf/dispatch [:connect "Abby"])]
     [button "Mike" #(rf/dispatch [:connect "Mike"])]]))

(defn menu
  []
  (println "Rendering menu!")
  [:div
   [:span (str "You are " @(rf/subscribe [:player]) ".")]
   [:h3 "Games"]
   (let [games @(rf/subscribe [:games])]
     (if (empty? games)
       [:span "You have no games."]
       [:ul
        (for [[id state] games]
          [:li {:key id} id ", Versus " (::game/opponent state)])]))
   [:h3 "Invitations"]
   (let [invitations @(rf/subscribe [:invitations])]
     (if (empty? invitations)
       [:span "You have no invitations."]
       [:ul (for [invitation invitations]
              [:li {:key invitation} (str invitation)])]))
   [:h3 "Messages"]
   (let [messages @(rf/subscribe [:messages])]
     (if (empty? messages)
       [:span "No messages."]
       [:ul (for [message messages]
              [:li {:key message} (str message)])]))])

(defn loading
  []
  [:div
   [:span "Loading..."]
   [:span @(rf/subscribe [:status-message])]
   (button "Refresh" #(rf/dispatch [:initialize]))])

(defn error
  []
  [:div
   [:h1 "Error!"]
   [:p @(rf/subscribe [:error-message])]])

(defn app
  []
  (let [loading?  @(rf/subscribe [:loading?])
        screen @(rf/subscribe [:screen])
        status-message @(rf/subscribe [:status-message])]
    [:div
     (button "Refresh" #(rf/dispatch [:initialize]))
     [:p
      (if status-message status-message "No status message set.")
      (when loading? " Loading...") ]

     (case screen
       :player-selection [player-selection]
       :menu [menu]
       :error [error]
       (throw (js/Error. (str "Invalid screen: " screen))))]))

(ns milo.client.game.views
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [milo.game :as game]
            [milo.card :as card]
            [milo.player :as player]
            [milo.score :as score]
            [taoensso.timbre :as log]))


(defn splash []
  [:h5 @(rf/subscribe [:status-message])])

(defn error
  []
  [:div
   [:h5 "Error!"]
   [:p (str@(rf/subscribe [:error-message]))]])

(defn app
  []
  (let [screen @(rf/subscribe [:screen])]
    (case screen
      :splash [splash]
      :player-selection [player-selection]
      :game [game]
      :round-over [round-over]
      :game-over [game-over]
      :error [error]
      (throw (js/Error. (str "Invalid screen: " screen))))))

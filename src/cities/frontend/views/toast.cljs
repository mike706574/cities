(ns cities.frontend.views.toast
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(defn toast []
  (let [{:keys [message action-event action-label color] :as toast} @(rf/subscribe [:toast])]
    (println "MAYBE TOAST" toast)
    (if toast
      (do
        (println "RENDER TOAST" toast)
        [:div
         {:aria-hidden "false"
          :style {"zIndex" "10"}}
         [:div (or message "No message.")]
         (when action-event
           [:button
            {:type "button"
             :on-click #(rf/dispatch action-event)}
            (or action-label "Perform Action")])])
      [:div
       {:class ""
        :style {"zIndex" "10"}
        :aria-hidden "true"}
       [:div ""]])))

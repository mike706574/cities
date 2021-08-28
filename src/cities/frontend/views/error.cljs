(ns cities.frontend.views.error
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [cities.game :as game]
            [cities.card :as card]
            [cities.player :as player]
            [cities.score :as score]
            [cities.frontend.misc :refer [pretty]]
            [taoensso.timbre :as log]))

(defn error []
  (let [{:keys [error-message error-body]} @(rf/subscribe [:error])]
  [:div
   [:h5 "Error!"]
   [:p error-message]
   [:textarea {:rows "10" :cols "100"} error-body]]))

(ns milo.client.views.error
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

(defn error []
  (let [{:keys [error-message error-body]} @(rf/subscribe [:error])]
  [:div
   [:h5 "Error!"]
   [:p error-message]
   [:textarea {:rows "10" :cols "100"} error-body]]))

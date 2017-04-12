(ns misplaced-villages.client.menu.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]
            [misplaced-villages.client.menu.events]
            [misplaced-villages.client.menu.subs]
            [misplaced-villages.client.menu.views :as views]))

(enable-console-print!)

(defn db
  []
  (cljs.pprint/pprint @(rf/subscribe [:state])))

(defn ^:export sync
  []
  (rf/dispatch [:sync]))

(defn ^:export run-anonymous
  []
  (rf/dispatch-sync [:initialize-anonymous])
  (reagent/render [views/app] (js/document.getElementById "app")))

(defn ^:export run
  [player]
  (if (= player "")
    (log/error "No player provided!")
    (do
      (rf/dispatch-sync [:initialize player])
      (reagent/render [views/app] (js/document.getElementById "app")))))
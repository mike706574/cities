(ns milo.client.menu.core
  (:require [day8.re-frame.http-fx]
            [taoensso.timbre :as log]
            [milo.client.menu.events]
            [milo.client.menu.subs]
            [milo.client.menu.views :as views]
            [reagent.core :as reagent]
            [re-frame.core :as rf]))

(enable-console-print!)

(defn db
  []
  (cljs.pprint/pprint @(rf/subscribe [:db])))

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

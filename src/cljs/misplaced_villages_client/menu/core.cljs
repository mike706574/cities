(ns misplaced-villages-client.menu.core
  (:require [cljs.pprint :refer [pprint]]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [misplaced-villages-client.menu.events]
            [misplaced-villages-client.menu.subs]
            [misplaced-villages-client.menu.views :as views]))

(enable-console-print!)

(defn state
  []
  (pprint @(rf/subscribe [:game])))

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (reagent/render [views/app] (js/document.getElementById "app")))

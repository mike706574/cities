(ns misplaced-villages-client.game.core
  (:require [cljs.pprint :refer [pprint]]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [day8.re-frame.http-fx]
            [misplaced-villages-client.game.events]
            [misplaced-villages-client.game.subs]
            [misplaced-villages-client.game.views :as views]
            [taoensso.timbre :as log]))

(defn state
  []
  (pprint @(rf/subscribe [:game])))

(defn sub
  [event]
  (pprint @(rf/subscribe [(keyword event)])))

(defn ^:export run
  []
  (enable-console-print!)
  (log/set-level! :trace)
  (rf/dispatch-sync [:initialize])
  (reagent/render [views/app] (js/document.getElementById "app")))

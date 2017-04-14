(ns misplaced-villages.client.game.core
  (:require [cljs.pprint :refer [pprint]]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]
            [misplaced-villages.client.game.events]
            [misplaced-villages.client.game.subs]
            [misplaced-villages.client.game.views :as views]))

(enable-console-print!)
(log/set-level! :trace)

(defn db
  []
  (pprint @(rf/subscribe [:state])))

(defn sub
  [event]
  (pprint @(rf/subscribe [(keyword event)])))

(defn ^:export reinitialize
  []
  (rf/dispatch [:reinitialize]))

(defn ^:export run-anonymous
  []
  (log/debug "Running anonymously!")
  (rf/dispatch-sync [:initialize-anonymous])
  (reagent/render [views/app] (js/document.getElementById "app")))

(defn ^:export run
  [player game-id]
  (log/debug "Running!")
  (rf/dispatch-sync [:initialize player game-id])
  (reagent/render [views/app] (js/document.getElementById "app")))

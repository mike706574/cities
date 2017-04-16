(ns milo.client.game.core
  (:require [cljs.pprint :refer [pprint]]
            [day8.re-frame.http-fx]
            [reagent.core :as reagent]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]
            [milo.client.game.events]
            [milo.client.game.subs]
            [milo.client.game.views :as views]))

(enable-console-print!)
(log/set-level! :info)

(defn db
  []
  (pprint @(rf/subscribe [:db])))

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

(ns milo.client.core
  (:require [day8.re-frame.http-fx]
            [milo.client.events]
            [milo.client.subs]
            [milo.client.views :as views]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]))

(enable-console-print!)

(defn set-log-level
  [level]
  (log/set-level! (keyword level)))

(defn debug
  [msg]
  (log/debug msg))

(defn trace
  [msg]
  (log/trace msg))

(defn db
  []
  (cljs.pprint/pprint @(rf/subscribe [:db])))

(defn init
  [player]
  (if (= player "")
    (throw (js/Error. "No player provided."))
    (do
      (rf/dispatch-sync [:initialize player])
      (r/render [views/app] (js/document.getElementById "app")))))

(defn ^:export run
  [player]
  (log/info (str "Running application as " player "."))
  (set! js/client-player player)
  (init player))

(defn ^:export refresh []
  (log/info "Refreshing application.")
  (do (rf/dispatch-sync [:initialize])
      (r/render [views/app] (js/document.getElementById "app"))))

(ns cities.frontend.app
  (:require [ajax.core :as ajax]
            [cljs.core.async :as async :refer [chan close! timeout >!]]
            [clojure.string :as str]
            [day8.re-frame.http-fx]
            [cities.frontend.events]
            [cities.frontend.subs]
            [cities.frontend.views :as views]
            [cities.frontend.websocket :as websocket]
            [reagent.dom :as rd]
            [re-frame.core :as rf]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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

(defn toaster!
  []
  (log/info "Plugging in toaster...")
  (let [toaster (chan)]
    (go-loop [counter 1]
      (log/info (str "Waiting for toast..."))
      (if-let [toast (<! toaster)]
        (do (log/info (str "Toast #" counter ": " toast))
            (rf/dispatch [:toast toast])
            (<! (timeout (or (:length toast) 2000)))
            (log/info "Untoasting...")
            (rf/dispatch [:untoast])
            (<! (timeout 500))
            (recur (inc counter)))
        (log/info "No more toast...")))
    toaster))

(defn retrieve-state!
  [player]
  (let [ch (chan)]
    (go (log/debug (str "Retrieving player state." {:player player}))
      (ajax/ajax-request
         {:uri "/api/menu"
          :method :get
          :headers {"Player" player}
          :response-format (ajax/transit-response-format)
          :handler (fn [[ok? response]]
                     (go (>! ch (if ok?
                                  {:ok? true :state response}
                                  {:ok? false :response response}))))}))
    ch))

(defn init [player]
  (let [player (str/lower-case (subs (-> js/window .-location .-pathname) 1))]
    (log/info "Initializing app." {:player player})
    (go (let [state-chan (retrieve-state! player)
              ws-chan (websocket/connect! player)
              resps (<! (async/map vector [state-chan ws-chan]))]
          (if-not (every? :ok? resps)
            (rd/render [views/initialization-error resps] (js/document.getElementById "app"))
            (let [[{state :state} {websocket :websocket}] resps
                  toaster (toaster!)
                  system {:player player
                          :websocket websocket
                          :toaster toaster}]
              (set! js/system system)
              (rf/dispatch-sync [:initialize state system])
              (rd/render [views/app] (js/document.getElementById "app"))))))))

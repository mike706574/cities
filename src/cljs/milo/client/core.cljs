(ns milo.client.core
  (:require [ajax.core :as ajax]
            [cljs.core.async :as async :refer [chan close! timeout >!]]
            [clojure.string :as str]
            [day8.re-frame.http-fx]
            [milo.client.events]
            [milo.client.subs]
            [milo.client.views :as views]
            [milo.client.websocket :as websocket]
            [reagent.core :as r]
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
    (go (log/debug (str "Retrieving state for " player "."))
      (ajax/ajax-request
         {:uri "/api/menu"
          :method :get
          :headers {"Player" player}
          :response-format (ajax/transit-response-format)
          :handler (fn [[ok? response]]
                     (cljs.pprint/pprint response)
                     (go (>! ch (if ok?
                                  {:ok? true :state response}
                                  {:ok? false :response response}))))}))
    ch))

(defn ^:export run
  [player]
  (log/info (str "Running application as " player "."))
  (if (= player "")
    (throw (js/Error. "No player provided."))
    (go (let [state-chan (retrieve-state! player)
              ws-chan (websocket/connect! player)
              responses (<! (async/map vector [state-chan ws-chan]))]
          (cljs.pprint/pprint responses)
          (if (every? :ok? responses)
            (do
              (let [state (:state (first
                                   responses))
                    toaster (toaster!)
                    system {:player player
                            :websocket (:websocket (second responses))
                            :toaster toaster}]
                (set! js/system system)
                (rf/dispatch-sync [:initialize state system])
                (r/render [views/app] (js/document.getElementById "app"))))
            (r/render [views/initialization-error responses] (js/document.getElementById "app")))))))

(defn ^:export refresh
  []
  (log/debug "Refreshing!")
  (let [{:keys [websocket toaster player]} js/system]
    (.close websocket)
    (close! toaster)
    (run player)))

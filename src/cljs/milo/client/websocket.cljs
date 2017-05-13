(ns milo.client.websocket
  (:require [cljs.core.async :refer [chan >!]]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [re-frame.core :as rf]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn decode
  [message]
  (transit/read (transit/reader :json) message))

(defn encode
  [message]
  (transit/write (transit/writer :json) message))

(defn on-message
  [event]
  (let [message (decode (.-data event))]
    (rf/dispatch [:message message])))

(defn on-error
  [event]
  (rf/dispatch [:error event]))

(defn connect!
  [player]
  (let [ch (chan)]
    (go (let [secure? (= (.-protocol (.-location js/document)) "https:")
              protocol (if secure? "wss" "ws")
              port (-> js/window .-location .-port)
              host (-> js/window .-location .-hostname )
              base (if (str/blank? port) host (str host ":" port))
              url (str protocol "://" base "/api/websocket")]
          (log/debug (str "Establishing websocket connection to " url "."))
          (let [socket (js/WebSocket. url)]
            (set! (.-onopen socket)
                  (fn on-open
                    [_]
                    (log/debug "Websocket connection established; registering player.")
                    (set! (.-onerror socket)
                          (fn on-registration-error
                            [event]
                            (go (>! ch {:ok? false :event event}))))
                    (set! (.-onmessage socket)
                          (fn on-registered
                            [event]
                            (let [response (decode (.-data event))]
                              (log/debug "Player registered.")
                              (set! (.-onmessage socket) on-message)
                              (set! (.-onerror socket) on-error)
                              (go (>! ch {:ok? true :websocket socket})))))
                    (.send socket (encode player)))))))
    ch))

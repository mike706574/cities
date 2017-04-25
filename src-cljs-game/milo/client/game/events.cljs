(ns milo.client.game.events
  (:require [ajax.core :as ajax]
            [cognitect.transit :as transit]
            [milo.game :as game]
            [milo.player :as player]
            [milo.move :as move]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]))

(defn pretty [x] (with-out-str (cljs.pprint/pprint x)))

(defmulti handle-message
  (fn [db message]
    (log/debug "Received message: " (pretty message))
    (:milo/status message)))


(defn decode
  [message]
  (transit/read (transit/reader :json) message))

(defn encode
  [message]
  (transit/write (transit/writer :json) message))

(defn handle-socket-event
  [event]
  (let [data (.-data event)
        message (decode data)]
    (rf/dispatch [:message message])))

(defn connect
  [player game-id]
  (log/debug "Starting websocket...")
  (if-let [socket (js/WebSocket. "ws://goose:8001/game-websocket")]
    (do (set! (.-onopen socket) #(rf/dispatch [:socket-open]))
        {:socket socket
         :player player
         :game-id game-id
         :screen :splash
         :status-message "Loading page..."})
    {:screen :error
     :error-message "Failed to create socket."}))

(rf/reg-event-db
 :message
 (fn [db [_ message]]
   (handle-message db message)))

(rf/reg-event-db
 :socket-open
 (fn [{:keys [:socket :player :game-id] :as db} _]
   (log/debug "Socket open! Authenticating as " player " for game " game-id ".")
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket (encode {::player/id player
                          ::game/id game-id}))
   db))

(rf/reg-event-db
 :initialize-anonymous
 (fn [_ _]
   {:screen :player-selection
    :status-message "Selecting player."}))

(rf/reg-event-db
 :initialize
 (fn [_ [_ player game-id]]
   (connect player game-id)))

(rf/reg-event-db
 :reinitialize
 (fn [{:keys [:player :game-id]} _]
   (connect player game-id)))

(rf/reg-event-db
 :connected
 (fn [db [_ message]]))


(rf/reg-event-db
 :error
 (fn [db [_ message]]
   (assoc db
     :screen :error
     :error-message message)))

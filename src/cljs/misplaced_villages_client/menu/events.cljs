(ns misplaced-villages-client.menu.events
  (:require [re-frame.core :as rf]
            [cljs.pprint :refer [pprint]]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [misplaced-villages.game :as game]
            [misplaced-villages.player :as player]
            [misplaced-villages.move :as move]
            [taoensso.timbre :as log :refer-macros [debug]]))

(defn handle-socket-event
  [event]
  (let [data (.-data event)
        message (cljs.reader/read-string data)]
    (if-let [status (:menu/status message)]
      (rf/dispatch [status message])
      (rf/dispatch [:error (str "Invalid message: " message)]))))

(defn menu
  [player]
  (if-let [socket (js/WebSocket. "ws://goose:8001/menu-websocket")]
    (do (set! (.-onopen socket) #(rf/dispatch [:socket-open socket player]))
        {:app/socket socket
         :app/player player
         :app/status-message "Waiting for socket to open."
         :app/loading? true})
    {:app/screen :error
     :app/error-message "Failed to create socket."}))

(rf/reg-event-db
 :socket-open
 (fn [db [_ socket player]]
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket (pr-str player))
   (assoc db :app/status-message "Socket open.")))

(rf/reg-event-db
 :initialize
 (fn [_ _]
   {:app/screen :player-selection
    :app/loading? false
    :app/status-message "Selecting player."}))

(rf/reg-event-db
 :connect
 (fn [db [_ player]]
   (merge db (menu player))))

(rf/reg-event-db
 :connected
 (fn [db [_ message]]
   (assoc db
          :app/loading? false
          :app/screen :menu
          :app/games (:menu/games message))))

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :app/player player)))

(rf/reg-event-db
 :error
 (fn [db [_ message]]
   (println (str "Error: " message))
   (assoc db
     :app/screen :error
     :app/error-message message)))

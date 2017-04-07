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
    (rf/dispatch [:message message])))

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

(defmulti handle-message
  (fn [db message]
    (println "FOOO:")
    (let [status (:menu/status message)]
      (println "STATUS:" status)
      status)))

(defmethod handle-message :state
  [db message]
  (merge db (:menu/state message)))

(defmethod handle-message :invited
  [{player :app/player :as db} {opponent :menu/opponent}]
  (-> db
      (update :menu/invitations conj [opponent player])
      (update :menu/messages conj (str opponent " invited you to play!"))))

(defmethod handle-message :invitation-created
  [{player :app/player :as db} {opponent :menu/opponent}]
  (-> db
      (update :menu/invitations conj [player opponent])
      (update :menu/messages conj (str "Invitation sent to " opponent "!"))))

(defn display-menu
  [db]
  (assoc db
         :app/status-message "Displaying menu."
         :app/screen :menu
         :app/loading? false))

(rf/reg-event-db
 :message
 (fn [db [_ message]]
   (println "MESSAGE:" message)
   (-> db
       (display-menu)
       (handle-message message))))

(rf/reg-event-db
 :socket-open
 (fn [db [_ socket player]]
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket player)
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

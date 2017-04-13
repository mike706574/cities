(ns misplaced-villages.client.menu.events
  (:require [cognitect.transit :as transit]
            [misplaced-villages.game :as game]
            [misplaced-villages.player :as player]
            [misplaced-villages.move :as move]
            [re-frame.core :as rf]
            [misplaced-villages.client.menu.message :as message]
            [taoensso.timbre :as log]))

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
  [player]
  (if-let [socket (js/WebSocket. "ws://goose:8001/menu-websocket")]
    (do (set! (.-onopen socket) #(rf/dispatch [:socket-open]))
        {:app/socket socket
         :app/player player
         :app/screen :splash
         :app/status-message "Connecting..."})
    {:app/screen :error
     :app/error-message "Failed to create socket."}))

(defn display-menu
  [db]
  (assoc db
         :app/status-message "Displaying menu."
         :app/screen :menu
         :app/loading? false))

(rf/reg-event-db
 :message
 (fn [db [_ message]]
   (-> db
       (display-menu)
       (message/handle message))))

(rf/reg-event-db
 :socket-open
 (fn [{:keys [:app/socket :app/player] :as db} _]
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket (encode player))
   (assoc db :app/status-message "Authenticating...")))

(rf/reg-event-db
 :initialize
 (fn [db [_ player]]
   (log/info (str "Initializing as " player "!"))
   (connect player)))

(rf/reg-event-db
 :initialize-anonymous
 (fn [db _]
   (log/info "Initializing anonymous!")
   {:app/screen :player-selection
    :app/loading? false
    :app/status-message "Selecting player."}))

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :app/player player)))

(rf/reg-event-db
 :error
 (fn [db [_ message]]
   (assoc db
     :app/screen :error
     :app/error-message message)))

(rf/reg-event-db
 :sync
 (fn [{socket :app/socket :as db} _]
   (log/info "Syncing!")
   (if socket
     (do (log/info "Syncing!")
         (.send socket (encode {:menu/status :sync}))))
   db))

(rf/reg-event-db
 :send-invite
 (fn [{:keys [:app/socket :app/player] :as db} [_ opponent]]
   (log/debug (str "Sending invite to " opponent "."))
   (.send socket (encode {:menu/status :send-invite :menu/player opponent}))
   db))

(rf/reg-event-db
 :accept-invite
 (fn [{:keys [:app/player :app/socket] :as db} [_ opponent]]
   (log/debug (str "Accepting invite from " opponent "."))
   (.send socket (encode {:menu/status :accept-invite :menu/player opponent}))
   (update db :menu/received-invites disj [opponent player])))

(rf/reg-event-db
 :reject-invite
 (fn [{:keys [:app/player :app/socket] :as db} [_ opponent]]
   (log/debug (str "Rejecting invite from " opponent "."))
   (.send socket (encode {:menu/status :reject-invite :menu/player opponent}))
   (update db :menu/received-invites disj [opponent player])))

(rf/reg-event-db
 :cancel-invite
 (fn [{:keys [:app/player :app/socket] :as db} [_ opponent]]
   (log/debug (str "Canceling invite to " opponent "."))
   (.send socket (encode {:menu/status :cancel-invite :menu/player opponent}))
   (update db :menu/sent-invites disj [player opponent])))

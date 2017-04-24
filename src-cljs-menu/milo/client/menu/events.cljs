(ns milo.client.menu.events
  (:require [ajax.core :as ajax]
            [cognitect.transit :as transit]
            [milo.game :as game]
            [milo.player :as player]
            [milo.menu :as menu]
            [milo.move :as move]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]))

(defmulti handle-message
  (fn [db message]
    (log/debug "Received message:" message)
    (:milo/status message)))

(defmethod handle-message :state
  [db message]
  (log/info "Setting state!")
  (merge db )
  (merge db message))

(defmethod handle-message :sent-invite
  [{events :events :as db} {event-id :milo/event-id invite :milo.menu/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :messages conj (str "Invite sent to " (second invite) "!"))
        (update ::menu/sent-invites conj invite))))

(defmethod handle-message :received-invite
  [{events :events :as db} {event-id :milo/event-id invite ::menu/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update ::menu/received-invites conj invite)
        (update :messages conj (str (first invite) " invited you to play!")))))

(defmethod handle-message :sent-invite-rejected
  [{events :events :as db} {event-id :milo/event-id invite ::menu/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update ::menu/sent-invites disj invite)
        (update :messages conj (str (second invite) " rejected your invite!")))))

(defmethod handle-message :sent-invite-canceled
  [{events :events :as db} {event-id :milo/event-id invite ::menu/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update ::menu/sent-invites disj invite)
        (update :messages conj (str "You canceled your invite for " (second invite) ".")))))

(defmethod handle-message :received-invite-canceled
  [{events :events :as db} {event-id :milo/event-id invite ::menu/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update ::menu/received-invites disj invite)
        (update :messages conj (str (first invite) " canceled your invite!")))))

(defmethod handle-message :received-invite-rejected
  [{events :events :as db} {event-id :milo/event-id invite ::menu/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update ::menu/received-invites disj invite)
        (update :messages conj (str "You rejected "(first invite) "'s invite!")))))

(defmethod handle-message :game-created
  [{events :events :as db} {event-id :milo/event-id

                            game :milo.game/game}]
  (if (contains? events event-id)
    db
    (let [{:keys [::game/id ::game/opponent ::game/turn ::game/round-number]} game]
      (log/debug (str "Created game: " game))
      (-> db
          (update :events conj event-id)
          (update ::menu/active-games assoc id game)
          (update :messages conj (str "Game " id " created against " (::game/opponent game) "."))))))

(defmethod handle-message :error
  [db {error-message ::menu/error-message}]
  (assoc db :screen :error :error-message :error-message))

(defn decode
  [message]
  (transit/read (transit/reader :json) message))

(defn encode
  [message]
  (transit/write (transit/writer :json) message))

(rf/reg-event-fx
 :take-turn
 (fn [{:keys [db]} _]
   (let [{:keys [:card :destination :source :player]} db
         move (move/move player card destination source)
         uri (str "/api/game/" (:game-id db))]
     {:db (dissoc db :move-message)
      :http-xhrio {:method :put
                   :uri uri
                   :params move
                   :headers {"Player" player}
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success [:turn-taken]
                   :on-failure [:turn-rejected]}})))

(defn handle-socket-event
  [event]
  (let [data (.-data event)
        message (decode data)]
    (rf/dispatch [:message message])))

(defn connect
  [player]
  (if-let [socket (js/WebSocket. "ws://goose:8001/menu-websocket")]
    (do (set! (.-onopen socket) #(rf/dispatch [:socket-open]))
        {:socket socket
         :player player
         :screen :splash
         :events #{}
         :status-message "Loading page..."})
    {:screen :error
     :error-message "Failed to create socket."}))

(rf/reg-event-db
 :initialize
 (fn [db [_ player]]
   (log/info (str "Initializing as " player "!"))
   (connect player)))

(defn display-menu
  [db]
  (assoc db
         :status-message "Displaying menu."
         :screen :menu
         :loading? false))

(rf/reg-event-db
 :message
 (fn [db [_ message]]
   (-> db
       (display-menu)
       (handle-message message))))

(rf/reg-event-db
 :socket-open
 (fn [{:keys [:socket :player] :as db} _]
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket (encode player))
   (assoc db :status-message "Loading page...")))

(rf/reg-event-db
 :initialize-anonymous
 (fn [db _]
   (log/info "Initializing anonymous!")
   {:screen :player-selection
    :loading? false
    :status-message "Selecting player."}))

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :player player)))

(rf/reg-event-db
 :error
 (fn [db [_ message]]
   (assoc db
     :screen :error
     :error-message message)))

(rf/reg-event-db
 :sync
 (fn [{socket :socket :as db} _]
   (log/info "Syncing!")
   (if socket
     (do (log/info "Syncing!")
         (.send socket (encode {::menu/status :sync}))))
   db))

(rf/reg-event-fx
 :send-invite
 (fn [{db :db} [_ opponent]]
   (let [player (:player db)
         invite [player opponent]]
     {:db (dissoc db :move-message)
      :http-xhrio {:method :post
                   :uri "/api/invite"
                   :headers {"Player" player}
                   :params invite
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success [:sent-invite]
                   :on-failure [:failed-to-send-invite]}})))

(rf/reg-event-db
 :sent-invite
 (fn [db [_ response]]
   (handle-message db response)))

(rf/reg-event-db
 :failed-to-send-invite
 (fn [db [_ {status :status response :response :as failure}]]
   (cljs.pprint/pprint failure)
   (merge db {:screen :error
              :error-message response})))

(rf/reg-event-fx
 :cancel-invite
 (fn [{db :db} [_ opponent]]
   (let [player (:player db)
         invite [player opponent]]
     {:db (dissoc db :move-message)
      :http-xhrio {:method :delete
                   :uri (str "/api/invite/" player "/" opponent)
                   :headers {"Player" player}
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success [:deleted-invite]
                   :on-failure [:failed-to-delete-invite]}})))

(rf/reg-event-fx
 :reject-invite
 (fn [{db :db} [_ opponent]]
   (let [player (:player db)
         invite [player opponent]]
     {:db (dissoc db :move-message)
      :http-xhrio {:method :delete
                   :uri (str "/api/invite/" opponent "/" player)
                   :headers {"Player" player}
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success [:deleted-invite]
                   :on-failure [:failed-to-delete-invite]}})))

(rf/reg-event-db
 :deleted-invite
 (fn [db [_ response]]
   (handle-message db response)))

(rf/reg-event-db
 :failed-to-delete-invite
 (fn [db [_ {status :status response :response :as failure}]]
   (cljs.pprint/pprint failure)
   (merge db {:screen :error
              :error-message response})))

(rf/reg-event-fx
 :accept-invite
 (fn [{db :db} [_ opponent]]
   (let [player (:player db)
         invite [opponent player]]
     (log/debug (str "Accepting invite: " invite))
     {:db (dissoc db :move-message)
      :http-xhrio {:method :post
                   :uri (str "/api/game")
                   :headers {"Player" player}
                   :params invite
                   :format (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success [:game-created]
                   :on-failure [:failed-to-accept-invite]}})))

(rf/reg-event-db
 :game-created
 (fn [db [_ response]]
   (handle-message db response)))

(rf/reg-event-db
 :failed-to-accept-invite
 (fn [db [_ response]]
   (cljs.pprint/pprint response)
   (handle-message db response)))

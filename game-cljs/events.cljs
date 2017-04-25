(ns milo.client.events
  (:require [ajax.core :as ajax]
            [cognitect.transit :as transit]
            [clojure.spec :as s]
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
  [db {:keys [:milo/active-games :milo/sent-invites :milo/received-invites]}]
  (assoc db
         :screen :menu
         :active-games active-games
         :sent-invites sent-invites
         :received-invites received-invites))

(defmethod handle-message :sent-invite
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite :as response}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :messages conj (str "Invite sent to " (second invite) "!"))
        (update :sent-invites conj invite))))

(defmethod handle-message :received-invite
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :received-invites conj invite)
        (update :messages conj (str (first invite) " invited you to play!")))))

(defmethod handle-message :sent-invite-rejected
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (log/debug "Sent invite rejected:" invite)
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :sent-invites disj invite)
        (update :messages conj (str (second invite) " rejected your invite!")))))

(defmethod handle-message :sent-invite-canceled
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (log/debug "Sent invite canceled:" invite)
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :sent-invites disj invite)
        (update :messages conj (str "You canceled your invite for " (second invite) ".")))))

(defmethod handle-message :received-invite-canceled
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (log/debug "Received invite canceled:" invite)
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :received-invites disj invite)
        (update :messages conj (str (first invite) " canceled your invite!")))))

(defmethod handle-message :received-invite-rejected
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (log/debug "Received invite rejected:" invite)
  (if (contains? events event-id)
    db
    (-> db
        (update :events conj event-id)
        (update :received-invites disj invite)
        (update :messages conj (str "You rejected "(first invite) "'s invite!")))))

(defmethod handle-message :game-created
  [{player :player events :events :as db} {:keys [:milo/event-id :milo.game/game :milo/invite]}]
  (if (contains? events event-id)
    db
    (let [{:keys [:milo.game/id :milo.game/opponent :milo.game/turn :milo.game/round-number]} game
          message (str "Game " id " created against " opponent ".")
          invites (if (= player (first invite)) :sent-invites :received-invites)]
      (log/debug (str "Created game " id "."))
      (-> db
          (update :events conj event-id)
          (update :active-games assoc id game)
          (update invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :error
  [db {error-message :milo/error-message}]
  (assoc db :screen :error :error-message :error-message))

(defmethod handle-message :taken
  [db {move :milo.move/move game :milo.game/game}]
  (let [message (str (:milo.player/id move) " took a turn.")]
    (assoc db :game game :status-message message)))

(defmethod handle-message :round-over
  [db {move :milo.move/move game :milo.game/game}]
  (let [message (str (:milo.player/id move) " took a turn and ended the round.")]
    (assoc db
           :screen :round-over
           :game game
           :status-message message)))

(defmethod handle-message :game-over
  [db {move :milo.move/move game :milo.game/game}]
  (let [message (str (:milo.player/id move) " took a turn and ended the game.")]
    (assoc db :game game :screen :game-over :status-message )))

(defn show-menu
  [db game-id]
  (log/debug "Showing menu.")
  (-> db
      (dissoc :game-id :card :destination :source :move-message)
      (assoc :screen :menu :status-mesage "Opened menu.")))

(defn show-game
  [db game-id]
  (let [game (get-in db [:active-games game-id])]
    (log/debug "Trying to show game" game-id)
    (log/debug "ACTIVE:" (keys (:active-games db)))
    (if (game/game-over? game)
      (assoc db
             :game-id game-id
             :loading? false
             :screen :game-over
             :status-message "Connected to completed game.")
      (assoc db
             :game-id game-id
             :loading? false
             :screen :game
             :card nil
             :destination :expedition
             :source :draw-pile
             :status-message "Connected to game."))))

(defn play-game
  [{db :db} [_ game-id]]
  (let [player (:player db)]
    (if-let [game (get-in db [:active-games game-id])]
      (if (:loaded? game)
        (show-game db game-id)
        {:db (-> db
                 (dissoc :move-message)
                 (assoc :loading? true))
         :http-xhrio {:method :get
                      :uri (str "/api/game/" game-id)
                      :headers {"Player" player}
                      :format (ajax/transit-request-format)
                      :response-format (ajax/transit-response-format)
                      :on-success [:game-retrieved]
                      :on-failure [:generic-error]}}))))

(defn show-retrieved-game
  [db [_ {game-id :milo.game/id game :milo.game/game}]]
  (-> db
      (update-in [:active-games game-id] #(-> %
                                              (merge game)
                                              (assoc :loaded? true)))
      (show-game game-id)))

(defn accept-invite
  [{db :db} [_ opponent]]
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
                  :on-failure [:generic-error]}}))

(defn send-invite
  [{db :db} [_ opponent]]
  (let [player (:player db)
        invite [player opponent]]
    {:db (dissoc db :move-message)
     :http-xhrio {:method :post
                  :uri "/api/invite"
                  :headers {"Player" player}
                  :params invite
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success [:invite-sent]
                  :on-failure [:generic-error]}}))

(defn cancel-invite
  [{db :db} [_ opponent]]
  (let [player (:player db)
        invite [player opponent]]
    {:db (dissoc db :move-message)
     :http-xhrio {:method :delete
                  :uri (str "/api/invite/" player "/" opponent)
                  :headers {"Player" player}
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success [:deleted-invite]
                  :on-failure [:generic-error]}}))

(defn reject-invite
  [{db :db} [_ opponent]]
  (let [player (:player db)
        invite [player opponent]]
    {:db (dissoc db :move-message)
     :http-xhrio {:method :delete
                  :uri (str "/api/invite/" opponent "/" player)
                  :headers {"Player" player}
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success [:deleted-invite]
                  :on-failure [:generic-error]}}))

(defn take-turn
  [{:keys [db]} _]
  (let [{:keys [:card :destination :source :player]} db
        move (move/move player card destination source)
        uri (str "/api/game/" (:game-id db))]
    {:http-xhrio {:method :put
                  :uri uri
                  :params move
                  :headers {"Player" player}
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success [:turn-taken]
                  :on-failure [:turn-rejected]}}))

(defn handle-turn-taken
  [db [_ response]]
  (let [{status :milo/status game ::game/game} response]
    (if (= status :game-over)
      (merge db {:game game
                 :screen :game-over
                 :status-message "You took a turn and ended the game."})
      (let [screen (case status
                     :round-over :round-over
                     :taken :game)
            status-message (case status
                             :round-over "You took a turn and ended the round."
                             :taken "You took a turn.")]
        (-> db
            (dissoc :move-message)
            (merge {:game game
                    :screen screen
                    :status-message status-message
                    :card nil
                    :destination :expedition
                    :source :draw-pile}))))))

(defn handle-turn-rejection
  [db [_ {status :status response :response :as failure}]]
  (if-not (= status 409)
    (assoc db :screen :error :error-message (str response))
    (if-not (= (:milo/status response) :turn-not-taken)
      (assoc db :screen :error :error-message (str response))
      (let [message (case (:milo.game/status response)
                      :too-low "Too low!"
                      :expedition-underway "Expedition already underway!"
                      :invalid-move "Invalid move!"
                      :discard-empty "Discard empty!"
                      :card-not-in-hand "Card not in hand!")]
        (assoc db :move-message message)))))

(defn handle-generic-error
  [db [_ {status :status response :response}]]
  (log/debug "lkeajwflkeajfkoejwaoifgajgfoaiwejgoiajw")
  (merge db {:screen :error
             :error-status-code status
             :error-message response}))

;; Initialization
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

(defn handle-message-event
  [db [_ response]]
  (handle-message db response))

(defn connect
  [db player]
  (if-let [socket (js/WebSocket. "ws://goose:8001/websocket")]
    (do (set! js/client-socket socket)
        (set! (.-onopen socket) #(rf/dispatch [:socket-open]))
        (assoc db :socket socket))
    (assoc db :screen :error :error-message "Failed to create socket.")))

(defn handle-socket-open
  [{:keys [:socket :player] :as db} _]
  (set! (.-onmessage socket) handle-socket-event)
  (.send socket (encode player))
  (assoc db :status-message "Loading page..."))

(s/def ::db (s/keys :req-un [::screen ::socket ::loading? ::status-message
                             ::error-message ::player ::events ::active-games
                             ::sent-invites ::received-invites ::card ::source
                             ::destination ::move-message]))

(defn initialize
  [_ [_ player]]
  (log/info (str "Initializing as " player "!"))
  (let [db {:screen :splash
            :socket nil
            :loading? true
            :status-message "Connecting..."
            :error-message nil
            :player player
            :events #{}
            :active-games {}
            :sent-invitse #{}
            :received-invites #{}
            :card nil
            :source nil
            :destination nil
            :move-message nil}]
    (connect db player)))

(defn handle-message-response
  [db [_ response]]
  (handle-message db response))

;; Events
(rf/reg-event-db :initialize initialize)
(rf/reg-event-db :socket-open handle-socket-open)
(rf/reg-event-db :handle-message handle-message-event)
(rf/reg-event-db :generic-error handle-generic-error)

(rf/reg-event-fx :send-invite send-invite)
(rf/reg-event-db :invite-sent handle-message-response)

(rf/reg-event-fx :cancel-invite cancel-invite)
(rf/reg-event-fx :reject-invite reject-invite)
(rf/reg-event-db :deleted-invite handle-message-response)

(rf/reg-event-fx :accept-invite accept-invite)
(rf/reg-event-db :game-created handle-message-response)

(rf/reg-event-fx :play-game play-game)
(rf/reg-event-db :game-retrieved show-retrieved-game)

(rf/reg-event-fx :take-turn take-turn)
(rf/reg-event-fx :turn-taken handle-turn-taken)
(rf/reg-event-fx :turn-rejected handle-turn-rejection)

(rf/reg-event-db :show-menu show-menu)

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :player player)))

(rf/reg-event-db
 :destination-change
 (fn [db [_ destination]]
   (-> db
       (assoc :destination destination)
       (dissoc :move-message))))

(rf/reg-event-db
 :source-change
 (fn [db [_ source]]
   (-> db
       (assoc :source source)
       (dissoc :move-message))))

(rf/reg-event-db
 :card-change
 (fn [db [_ card]]
   (log/debug (str "Changing card to " card))
   (-> db
       (assoc :card card)
       (dissoc :move-message))))

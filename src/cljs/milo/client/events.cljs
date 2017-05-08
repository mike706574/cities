(ns milo.client.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [milo.client.misc :refer [pretty]]
            [milo.data :as data]
            [milo.game :as game]
            [milo.player :as player]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [milo.client.macros :refer [guard-event]]))

(defn toast!
  [{toaster :toaster :as db} body]
  (go (>! toaster body))
  db)

(defn screen-toast!
  [target {:keys [toaster screen] :as db} body]
  (when (or (not screen) (= screen target))
    (go (>! toaster body)))
  db)

(def menu-toast! (partial screen-toast! :menu))
(def game-toast! (partial screen-toast! :game))

(defn- handle-turn-taken*
  [{player :player current-game-id :game-id :as db} event]
  (guard-event db event
    (let [{status :milo/status
           game-id :milo.game/id
           game :milo.game/game
           move :milo.game/move} event]
      (if-not (= game-id current-game-id)
        (assoc-in db [:active-games game-id] game)
        (let [screen (case status
                       :taken :game
                       :round-over :round-over
                       :game-over :game-over)
              message (data/move-sentence player move)]
          (game-toast! db {:message message})
          (-> db
              (assoc-in [:active-games game-id] game)
              (dissoc :card)
              (assoc :screen screen
                     :status-message message
                     :destination nil
                     :source :draw-pile)))))))

(defmulti handle-message
  (fn [db message]
    (let [status (:milo/status message)]
      status)))

(defmethod handle-message :error
  [db {error-message :milo/error-message}]
  (assoc db :screen :error :error-message :error-message))

(defmethod handle-message :sent-invite
  [db {invite :milo/invite :as event}]
  (log/debug "Handling message :sent-invite")
  (guard-event db event
               (let [recipient (second invite)
                     message (str "You invited " recipient " to play.")]
                 (menu-toast! db {:message message
                                  :action-label "Cancel"
                                  :action-event [:cancel-invite recipient]})
                 (-> db
                     (update :messages conj message)
                     (update :invites conj invite)))))

(defmethod handle-message :received-invite
  [db {invite :milo/invite :as event}]
  (guard-event db event
    (let [sender (first invite)
          message (str sender " invited you to play.")]
      (menu-toast! db {:message message
                       :action-label "Accept"
                       :action-event [:accept-invite sender]})
      (-> db
          (update :invites conj invite)
          (update :messages conj message)))))


(defmethod handle-message :sent-invite-rejected
  [db {invite :milo/invite :as event}]
  (log/debug "Sent invite rejected:" invite)
  (guard-event db event
    (let [message (str (second invite) " rejected your invite")]
      (menu-toast! db {:message message})
      (-> db
          (update :invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :sent-invite-canceled
  [db {invite :milo/invite :as event}]
  (log/debug "Sent invite canceled:" invite)
  (guard-event db event
               (let [message (str "You uninvited " (second invite) ".")]
                 (menu-toast! db {:message message})
                 (-> db
                     (update :invites disj invite)
                     (update :messages conj message)))))

(defmethod handle-message :received-invite-canceled
  [db {event-id :milo/event-id invite :milo/invite :as event}]
  (log/debug "Received invite canceled:" invite)
  (guard-event db event
    (-> db
        (update :invites disj invite)
        (update :messages conj (str (first invite) " canceled your invite!")))))

(defmethod handle-message :received-invite-rejected
  [db {invite :milo/invite :as event}]
  (log/debug "Received invite rejected:" invite)
  (guard-event db event
    (let [message (str "You rejected "(first invite) "'s invite!")]
      (menu-toast! db {:message message})
      (-> db
          (update :invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :game-created
  [{player :player :as db} {:keys [:milo.game/game :milo/invite] :as event}]
  (guard-event db event
    (let [{:keys [:milo.game/id :milo.game/opponent :milo.game/turn :milo.game/round-number]} game
          message (str "Game started against " opponent ".")]
      (log/debug (str "Created game " id "."))
      (menu-toast! db {:message message
                       :action-label "Play"
                       :action-event [:play-game id]})
      (-> db
          (update :active-games assoc id game)
          (update :invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :taken [db event] (handle-turn-taken* db event))
(defmethod handle-message :round-over [db event] (handle-turn-taken* db event))
(defmethod handle-message :game-over [db event] (handle-turn-taken* db event))

(defn show-menu
  [db _]
  (assoc db
         :screen :menu
         :status-message "Showing menu."
         :game-id nil
         :card nil
         :destination nil
         :source nil))

(defn show-game
  [db game-id]
  (let [game (get-in db [:active-games game-id])]
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
             :destination nil
             :source :draw-pile
             :status-message "Connected to game."))))

(defn play-game
  [{db :db} [_ game-id]]
  (let [player (:player db)]
    (if-let [game (get-in db [:active-games game-id])]
      (if (:loaded? game)
        {:db (show-game db game-id)}
        {:db (-> db
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
    {:db db
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
    {:db db
     :http-xhrio {:method :post
                  :uri "/api/invite"
                  :headers {"Player" player}
                  :params invite
                  :format (ajax/transit-request-format)
                  :response-format (ajax/transit-response-format)
                  :on-success [:invite-sent]
                  :on-failure [:failed-to-send-invite]}}))

(defn send-invite-failure
  [db [_ {status :status response :response :as failure}]]
  (if (= status 409)
    (menu-toast! db {:message (:milo.server/message response)})
    (assoc db :screen :error :error-message (str response))))

(defn cancel-invite
  [{db :db} [_ opponent]]
  (let [player (:player db)
        invite [player opponent]]
    {:db db
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
    {:db db
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
        move (game/move player card destination source)
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
  (handle-turn-taken* db response))

(defn handle-turn-failure
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
        (game-toast! db {:message message})
        (assoc db :card nil :destination nil :source nil)))))

(defn handle-generic-error
  [db [_ body]]
  (log/debug "Handling generic error.")
  (assoc db
         :screen :error
         :error-message "Generic error."
         :error-body (pretty body)))

(rf/reg-event-db
 :message
 (fn [db [_ response]]
   (handle-message db response)))

(rf/reg-event-db
 :initialize
 (fn [_ [_ state system]]
   (let [toaster (:toaster system)
         {player :milo.player/id
          active-games :milo/active-games
          avatars :milo/avatars
          invites :milo/invites} state]
     (log/debug "Initialized!")
     {:screen :menu
      :socket nil
      :player player
      :status-message "Initialized."
      :events {}
      :game-id nil
      :avatars avatars
      :active-games active-games
      :invites invites
      :card nil
      :source nil
      :destination nil
      :toaster toaster})))

;; Real events
(defn handle-message-response
  [db [_ response]]
  (handle-message db response))

(rf/reg-event-db :generic-error handle-generic-error)

(rf/reg-event-fx :send-invite send-invite)
(rf/reg-event-db :invite-sent handle-message-response)
(rf/reg-event-db
 :failed-to-send-invite
 (fn
   [db [_ {status :status response :response :as failure}]]
   (if (= status 409)
     (let [message (let [{:keys [:milo/status :milo/invite]} response
                         recipient (second invite)]
                     (case (:milo/status response)
                       :invite-already-sent (str "You've already invited " recipient ".")
                       :invite-already-received (str "You already have an invite from " recipient ".")
                       (pretty response)))]
       (menu-toast! db {:message message}))
     (assoc db :screen :error :error-message (str response)))))

(rf/reg-event-fx :cancel-invite cancel-invite)
(rf/reg-event-fx :reject-invite reject-invite)
(rf/reg-event-db :deleted-invite handle-message-response)
(rf/reg-event-fx :accept-invite accept-invite)

(rf/reg-event-db :game-created handle-message-response)

(rf/reg-event-fx :play-game play-game)
(rf/reg-event-db :game-retrieved show-retrieved-game)

(rf/reg-event-db :show-menu show-menu)

(rf/reg-event-fx :take-turn take-turn)
(rf/reg-event-db :turn-taken handle-turn-taken)
(rf/reg-event-db :turn-rejected handle-turn-failure)

(rf/reg-event-db
 :back-to-game
 (fn [db _]
   (assoc db :screen :game)))

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :player player)))

(rf/reg-event-db
 :select-destination
 (fn [db [_ destination]]
   (if (:card db)
     (assoc db :destination destination :source nil)
     db)))

(rf/reg-event-db
 :deselect-destination
 (fn [db [_ destination]]
   (assoc db :destination destination :source nil)))

(rf/reg-event-db
 :source-change
 (fn [db [_ source]]
   (assoc db :source source)))

(rf/reg-event-db
 :card-change
 (fn [db [_ card]]
   (assoc db :card card :destination nil :source nil)))

(rf/reg-event-db
 :toast
 (fn [db [_ toast]]
   (assoc db :toast toast)))

(rf/reg-event-db
 :untoast
 (fn [db [_ toast]]
   (dissoc db :toast)))

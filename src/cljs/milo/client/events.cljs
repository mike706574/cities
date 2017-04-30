(ns milo.client.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [milo.game :as game]
            [milo.menu :as menu]
            [milo.move :as move]
            [milo.player :as player]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [taoensso.timbre :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; TODO: Move to milo.
(defn card-description
  [{:keys [:milo.card/color :milo.card/type :milo.card/number]}]
  (let [color (-> color name str/capitalize)]
    (if (= type :wager)
      (str "a " color " wager card")
      (str color " " number))))

(defn move-sentence
  [{:keys [:milo.player/id :milo.card/card :milo.move/destination :milo.move/source]}]
  (str id " "
       (case destination
         :expedition "played"
         :discard-pile "discarded")
       " "
       (card-description card)
       " and drew "
       (if (= :draw-pile source)
         "a new card."
         (str "from the " (-> source name str/capitalize) " discard pile."))))
;; END

(defn toast!
  [{toaster :toaster :as db} body]
  (go (>! toaster body)))

(defn screen-toast!
  [target {:keys [toaster screen] :as db} body]
  (when (or (not screen) (= screen target))
    (go (>! toaster body))))

(def menu-toast! (partial screen-toast! :menu))
(def game-toast! (partial screen-toast! :game))

(defmulti handle-message
  (fn [db message]
    (let [status (:milo/status message)]
      status)))

(defmethod handle-message :error
  [db {error-message :milo/error-message}]
  (assoc db :screen :error :error-message :error-message))

(defmethod handle-message :sent-invite
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite :as response}]
  (if (contains? events event-id)
    db
    (let [recipient (second invite)
          message (str "You invited " recipient " to play.")]
      (menu-toast! db {:message message
                       :action-label "Cancel"
                       :action-event [:cancel-invite recipient]})
      (-> db
          (update :events conj event-id)
          (update :messages conj message)
          (update :sent-invites conj invite)))))

(defmethod handle-message :received-invite
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (if (contains? events event-id)
    db
    (let [sender (first invite)
          message (str sender " invited you to play.")]
      (menu-toast! db {:message message
                       :action-label "Accept"
                       :action-event [:accept-invite sender]})
      (-> db
          (update :events conj event-id)
          (update :received-invites conj invite)
          (update :messages conj message)))))


(defmethod handle-message :sent-invite-rejected
  [{events :events :as db} {event-id :milo/event-id invite :milo/invite}]
  (log/debug "Sent invite rejected:" invite)
  (if (contains? events event-id)
    db
    (let [message (str (second invite) " rejected your invite")]
      (menu-toast! db {:message message})
      (-> db
          (update :events conj event-id)
          (update :sent-invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :sent-invite-canceled
  [{events :events toaster :toaster :as db} {event-id :milo/event-id invite :milo/invite}]
  (log/debug "Sent invite canceled:" invite)
  (if (contains? events event-id)
    db
    (let [message (str "You uninvited " (second invite) ".")]
      (menu-toast! db {:message message})
      (-> db
          (update :events conj event-id)
          (update :sent-invites disj invite)
          (update :messages conj message)))))

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
    (let [message (str "You rejected "(first invite) "'s invite!")]
      (menu-toast! db {:message message})
      (-> db
          (update :events conj event-id)
          (update :received-invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :game-created
  [{player :player events :events :as db} {:keys [:milo/event-id :milo.game/game :milo/invite]}]
  (if (contains? events event-id)
    db
    (let [{:keys [:milo.game/id :milo.game/opponent :milo.game/turn :milo.game/round-number]} game
          message (str "Game started against " opponent ".")
          invites (if (= player (first invite)) :sent-invites :received-invites)]
      (log/debug (str "Created game " id "."))
      (menu-toast! db {:message message
                       :action-label "Play"
                       :action-event [:play-game id]})
      (-> db
          (update :events conj event-id)
          (update :active-games assoc id game)
          (update invites disj invite)
          (update :messages conj message)))))

(defmethod handle-message :taken
  [{:keys [events game-id] :as db} {:keys [:milo/event-id :milo.game/game :milo.move/move]}]
  (if (contains? events event-id)
    db
    (let [message (move-sentence move)]
      (game-toast! db {:message message})
      (-> db
          (assoc-in [:active-games game-id] game)
          (assoc :status-message message)))))

(defmethod handle-message :round-over
  [{:keys [events game-id] :as db} {:keys [:milo/event-id :milo.game/game :milo.move/move]}]
  (if (contains? events event-id)
    db
    (let [message (move-sentence move)]
      (game-toast! db {:message message})
      (-> db
          (assoc :screen :round-over :status-message message)
          (assoc-in [:active-games game-id] game)))))

(defmethod handle-message :game-over
  [{:keys [events game-id] :as db} {:keys [:milo/event-id :milo.game/game :milo.move/move]}]
  (if (contains? events event-id)
    db
    (let [message (move-sentence move)]
      (game-toast! db {:message message})
      (-> db
          (assoc :screen :game-over :status-message message)
          (assoc-in [:active-games game-id] game)))))

(defn show-menu
  [db _]
  (assoc db
         :screen :menu
         :status-message "Showing menu."
         :game-id nil
         :card nil
         :destination nil
         :source nil
         :move-message nil))

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
             :destination :expedition
             :source :draw-pile
             :status-message "Connected to game."))))

(defn play-game
  [{db :db} [_ game-id]]
  (let [player (:player db)]
    (if-let [game (get-in db [:active-games game-id])]
      (if (:loaded? game)
        {:db (show-game db game-id)}
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
  (let [{status :milo/status game :milo.game/game} response]
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
  [db [_ body]]
  (merge db {:screen :error
             :error-message "Generic error."
             :error-body (with-out-str (cljs.pprint/pprint body))}))

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
          sent-invites :milo/sent-invites
          received-invites :milo/received-invites} state]
     {:screen :menu
      :socket nil
      :player player
      :status-message "Initialized."
      :events #{}
      :active-games active-games
      :sent-invites sent-invites
      :received-invites received-invites
      :move-message nil
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
(rf/reg-event-db :turn-rejected handle-turn-rejection)

(rf/reg-event-db
 :back-to-game
 (fn [db _]
   (assoc db :screen :game)))

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

(rf/reg-event-db
 :toast
 (fn [db [_ toast]]
   (log/debug (str "Changing toast to " toast))
   (assoc db :toast toast)))

(rf/reg-event-db
 :untoast
 (fn [db [_ toast]]
   (log/debug (str "Untoasting" toast))
   (dissoc db :toast)))

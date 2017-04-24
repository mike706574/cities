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

(defmethod handle-message :connected
  [db {game ::game/game}]
  (log/debug "Connected!")
  (if (game/game-over? game)
    (assoc db
           :game game
           :loading? false
           :screen :game-over
           :status-message "Connected to completed game.")
    (assoc db
           :game game
           :loading? false
           :screen :game
           :card nil
           :destination :expedition
           :source :draw-pile
           :status-message "Connected to game.")))

(defmethod handle-message :taken
  [db {move ::move/move game ::game/game}]
  (let [message (str (::player/id move) " took a turn.")]
    (assoc db :game game :status-message message)))

(defmethod handle-message :round-over
  [db {move ::move/move game ::game/game}]
  (let [message (str (::player/id move) " took a turn and ended the round.")]
    (assoc db
           :screen :round-over
           :game game
           :status-message message)))

(defmethod handle-message :game-over
  [db {move ::move/move game ::game/game}]
  (let [message (str (::player/id move) " took a turn and ended the game.")]
    (assoc db :game game :screen :game-over :status-message )))

(rf/reg-event-fx
 :take-turn
 (fn [{:keys [db]} _]
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
                   :on-failure [:turn-rejected]}})))

(rf/reg-event-db
 :turn-taken
 (fn [db [_ response]]
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
                     :source :draw-pile})))))))

(rf/reg-event-db
 :turn-rejected
 (fn [db [_ {status :status response :response :as failure}]]
   (if-not (= status 409)
     (assoc db :screen :error :error-message (pretty response))
     (if-not (= (:milo/status response) :turn-not-taken)
       (assoc db :screen :error :error-message (pretty response))
       (let [message (case (:milo.game/status response)
                       :too-low "Too low!"
                       :expedition-underway "Expedition already underway!"
                       :invalid-move "Invalid move!"
                       :discard-empty "Discard empty!"
                       :card-not-in-hand "Card not in hand!")]
         (assoc db :move-message message))))))

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
 :error
 (fn [db [_ message]]
   (log/error (str "Error: " message))
   (assoc db
     :screen :error
     :error-message message)))

(rf/reg-event-db
 :round-screen
 (fn [db [_ message]]
   (assoc db :screen :game)))

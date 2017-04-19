(ns milo.client.game.events
  (:require [ajax.core :as ajax]
            [cognitect.transit :as transit]
            [milo.game :as game]
            [milo.player :as player]
            [milo.move :as move]
            [milo.client.game.message :as message]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]))

(rf/reg-event-fx
 :take-turn
 (fn [{:keys [db]} _]
   (let [{:keys [:app/card :app/destination :app/source :app/player]} db
         move (move/move player card destination source)
         uri (str "/api/game/" (:app/game-id db))]
     {:db (dissoc db :app/move-message)
      :http-xhrio {:method :put
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
   (let [{status ::game/status game ::game/state} response]
     (if (= status :game-over)
       (merge db {:app/game game
                  :app/screen :game-over
                  :app/status-message "You took a turn and ended the game."})
       (let [screen (case status
                      :round-over :round-over
                      :taken :game)
             status-message (case status
                              :round-over "You took a turn and ended the round."
                              :taken "You took a turn.")]
         (merge db {:app/game game
                    :app/screen screen
                    :app/status-message status-message
                    :app/card nil
                    :app/destination :expedition
                    :app/source :draw-pile}))))))

(rf/reg-event-db
 :turn-rejected
 (fn [db [_ {status :status response :response :as failure}]]
   (cljs.pprint/pprint failure)
   (if-not (= status 409)
     (merge db {:app/screen :error
                :app/error-message response})
     (let [message (case (::game/status response)
                     :too-low "Too low!"

                     :expedition-underway "Expedition already underway!"
                     ;; TODO: These should never happen - handle them
                     ;;       differently.
                     :invalid-move "Invalid move!"
                     :discard-empty "Discard empty!"
                     :card-not-in-hand "Card not in hand!")]
       (assoc db :app/move-message message)))))

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
        {:app/socket socket
         :app/player player
         :app/game-id game-id
         :app/screen :splash
         :app/status-message "Loading page..."})
    {:app/screen :error
     :app/error-message "Failed to create socket."}))

(rf/reg-event-db
 :message
 (fn [db [_ message]]
   (message/handle db message)))

(rf/reg-event-db
 :socket-open
 (fn [{:keys [:app/socket :app/player :app/game-id] :as db} _]
   (log/debug "Socket open! Authenticating as " player " for game " game-id ".")
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket (encode {::player/id player
                          ::game/id game-id}))
   (println "here")
   db))

(rf/reg-event-db
 :initialize-anonymous
 (fn [_ _]
   {:app/screen :player-selection
    :app/status-message "Selecting player."}))

(rf/reg-event-db
 :initialize
 (fn [_ [_ player game-id]]
   (connect player game-id)))

(rf/reg-event-db
 :reinitialize
 (fn [{:keys [:app/player :app/game-id]} _]
   (connect player game-id)))

(rf/reg-event-db
 :connected
 (fn [db [_ message]]))

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :app/player player)))

(rf/reg-event-db
 :destination-change
 (fn [db [_ destination]]
   (-> db
       (assoc :app/destination destination)
       (dissoc :app/move-message))))

(rf/reg-event-db
 :source-change
 (fn [db [_ source]]
   (-> db
       (assoc :app/source source)
       (dissoc :app/move-message))))

(rf/reg-event-db
 :card-change
 (fn [db [_ card]]
   (log/debug (str "Changing card to " card))
   (-> db
       (assoc :app/card card)
       (dissoc :app/move-message))))

(rf/reg-event-db
 :error
 (fn [db [_ message]]
   (log/error (str "Error: " message))
   (assoc db
     :app/screen :error
     :app/error-message message)))

(rf/reg-event-db
 :round-screen
 (fn [db [_ message]]
   (assoc db :app/screen :game)))

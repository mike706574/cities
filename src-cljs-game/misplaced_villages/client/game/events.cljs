(ns misplaced-villages.client.game.events
  (:require [re-frame.core :as rf]
            [misplaced-villages.game :as game]
            [misplaced-villages.player :as player]
            [misplaced-villages.move :as move]
            [misplaced-villages.client.game.message :as message]
            [taoensso.timbre :as log]))

(defn handle-socket-event
  [event]
  (let [data (.-data event)
        message (cljs.reader/read-string data)]
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
         :app/status-message "Authenticating..."})
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
   (.send socket (pr-str {::player/id player
                          ::game/id game-id}))
   (assoc db :app/status-message "Socket open.")))

(rf/reg-event-db
 :initialize-anonymous
 (fn [_ _]
   {:app/screen :player-selection
    :app/loading? false
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
 (fn [db [_ message]]
   (let [state (::game/state message)]
     (if (game/game-over? state)
       (assoc db
              :app/game state
              :app/loading? false
              :app/screen :game-over
              :app/status-message "Connected.")
       (assoc db
              :app/game state
              :app/loading? false
              :app/screen :game
              :app/card nil
              :app/destination :expedition
              :app/source :draw-pile
              :app/status-message "Connected.")))))

(rf/reg-event-db
 :player-change
 (fn [db [_ player]]
   (assoc db :app/player player)))

(rf/reg-event-db
 :destination-change
 (fn [db [_ destination]]
   (assoc db :app/destination destination)))

(rf/reg-event-db
 :source-change
 (fn [db [_ source]]
   (assoc db :app/source source)))

(rf/reg-event-db
 :card-change
 (fn [db [_ card]]
   (log/debug (str "Changing card to " card))
   (assoc db :app/card card)))

(rf/reg-event-db
 :move
 (fn [db [_]]
   (let [{:keys [:app/socket
                 :app/card
                 :app/destination
                 :app/source
                 :app/player]} db
         move (move/move player
                         card
                         destination source)]
     (log/debug "Sending move:" move)
     (.send socket (pr-str move))
     (assoc db
       :app/card nil
       :app/destination :expedition
       :app/source :draw-pile)
     db)))

(rf/reg-event-db
 :error
 (fn [db [_ message]]
   (log/error (str "Error: " message))
   (assoc db
     :app/screen :error
     :app/error-message message)))

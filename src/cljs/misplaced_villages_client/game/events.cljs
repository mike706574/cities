(ns misplaced-villages-client.game.events
  (:require [re-frame.core :as rf]
            [cljs.pprint :refer [pprint]]
            [day8.re-frame.http-fx]
            [ajax.core :as ajax]
            [misplaced-villages.game :as game]
            [misplaced-villages.player :as player]
            [misplaced-villages.move :as move]
            [taoensso.timbre :as log]))

(defn handle-socket-event
  [event]
  (let [data (.-data event)
        message (cljs.reader/read-string data)]
    (log/debug "Received message:" message)
    (if-let [status (::game/status message)]
      (rf/dispatch [status message])
      (rf/dispatch [:error (str "Invalid message: " message)]))))

(defn play
  [player]
  (log/debug "Starting websocket...")
  (if-let [socket (js/WebSocket. "ws://goose:8001/game-websocket")]
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
   (log/debug "Socket open...")
   (set! (.-onmessage socket) handle-socket-event)
   (.send socket (pr-str {::player/id player
                          ::game/id "1"}))
   (assoc db :app/status-message "Socket open.")))

(rf/reg-event-db
 :initialize
 (fn [_ _]
   {:app/screen :player-selection
    :app/loading? false
    :app/status-message "Selecting player."}))

(rf/reg-event-db
 :play
 (fn [db [_ player]]
   (merge db (play player))))

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
 :player-connected
 (fn [db [_ {player ::player/id}]]
   (assoc db :app/status-message (str player " connected."))))

;; Move completed
(rf/reg-event-db
 :taken
 (fn [db [_ {player ::player/id
             game ::game/state}]]
   (assoc db
     :app/game game
     :app/move-message nil
     :app/status-message (str player " took a turn."))))

(rf/reg-event-db
 :round-over
 (fn [{player :app/player :as db} [_ {player ::player/id
                                      game ::game/state}]]
   (assoc db
          :app/game game
          :app/move-message nil
          :app/status-message (str player " took a turn and ended the round."))))

(rf/reg-event-db
 :game-over
 (fn [{player :app/player :as db} [_ {player ::player/id
                                      game ::game/state}]]
   (assoc db
          :app/screen :game-over
          :app/move-message nil
          :app/status-message (str player " took a turn and ended the round."))))

;; Move invalid
(rf/reg-event-db
 :too-low
 (fn [db [_ {player ::player/id
             game ::game/state}]]
   (assoc db :app/move-message (str "Tow low!"))))

(rf/reg-event-db
 :invalid-move
 (fn [{player :app/player :as db} [_ {action-player ::player/id}]]
   (when (= player action-player)
       (assoc db :app/move-message (str "Invalid move!")))))

(rf/reg-event-db
 :expedition-underway
 (fn [{player :app/player :as db} [_ {action-player ::player/id}]]
   (when (= player action-player)
       (assoc db :app/move-message (str "Expedition already underway!!")))))

;; Error states
(rf/reg-event-db
 :discard-empty
 (fn [{player :app/player :as db} [_ state]]
   (log/error (str "Discard empty error."))
   (assoc db
          :app/screen :error
          :app/error-message
          :app/error-body (with-out-str (pprint state)))))

(rf/reg-event-db
 :card-not-in-hand
 (fn [{player :app/player :as db} [_ state]]
   (log/error (str "Card not in hand error."))
   (assoc db
          :app/screen :error
          :app/error-message
          :app/error-body (with-out-str (pprint state)))))
;; End

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

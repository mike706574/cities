(ns misplaced-villages.client.game.message
  (:require [cljs.pprint :refer [pprint]]
            [misplaced-villages.game :as game]
            [misplaced-villages.player :as player]
            [taoensso.timbre :as log]))

(defmulti handle
  (fn [db message]
    (::game/status message)))

(defmethod handle :connected
  [db {game ::game/state}]
  (assoc db
         :app/game game
         :app/loading? false
         :app/screen :game
         :app/card nil
         :app/destination :expedition
         :app/source :draw-pile
         :app/status-message "Connected."))

(defmethod handle :player-connected
  [db {player ::player/id :as message}]
  (assoc db :app/status-message (str player " connected.")))

(defmethod handle :taken
  [db {player ::player/id game ::game/state}]
  (assoc db
         :app/game game
         :app/move-message nil
         :app/status-message (str player " took a turn.")))

(defmethod handle :round-over
  [db {player ::player/id game ::game/state}]
  (assoc db
         :app/game game
         :app/move-message nil
         :app/status-message (str player " took a turn and ended the round.")))

(defmethod handle :game-over
  [db {player ::player/id game ::game/state}]
  (assoc db
         :app/screen :game-over
         :app/move-message nil
         :app/status-message (str player " took a turn and ended the game.")))

(defmethod handle :too-low
  [db {player ::player/id game ::game/state}]
  (assoc db :app/move-message (str "Tow low!")))

(defmethod handle :invalid-move
  [{player :app/player :as db} {action-player ::player/id}]
  (if (= player action-player)
    (assoc db :app/move-message (str "Invalid move!"))
    db))

(defmethod handle :expedition-underway
  [{player :app/player :as db} {action-player ::player/id}]
  (if (= player action-player)
    (assoc db :app/move-message (str "Expedition already underway!!"))
    db))

(defmethod handle :discard-empty
  [db [_ state]]
  (log/error (str "Discard empty error."))
  (assoc db
         :app/screen :error
         :app/error-message
         :app/error-body (with-out-str (pprint state))))

(defmethod handle :card-not-in-hand
  [db [_ state]]
  (log/error (str "Card not in hand error."))
  (assoc db
         :app/screen :error
         :app/error-message
         :app/error-body (with-out-str (pprint state))))

(ns milo.client.game.message
  (:require [cljs.pprint :refer [pprint]]
            [milo.game :as game]
            [milo.move :as move]
            [milo.player :as player]
            [taoensso.timbre :as log]))

(defmulti handle
  (fn [db message]
    (::game/status message)))

(defmethod handle :connected
  [db {game ::game/game}]
  (log/debug "Connected!")
  (if (game/game-over? game)
    (assoc db
           :app/game game
           :app/loading? false
           :app/screen :game-over
           :app/status-message "Connected to completed game.")
    (assoc db
           :app/game game
           :app/loading? false
           :app/screen :game
           :app/card nil
           :app/destination :expedition
           :app/source :draw-pile
           :app/status-message "Connected to game.")))

(defmethod handle :taken
  [db {move ::move/move game ::game/game}]
  (let [message (str (::player/id move) " took a turn.")]
    (assoc db :app/game game :app/status-message message)))

(defmethod handle :round-over
  [db {move ::move/move game ::game/game}]
  (let [message (str (::player/id move) " took a turn and ended the round.")]
    (assoc db
           :app/screen :round-over
           :app/game game
           :app/status-message message)))

(defmethod handle :game-over
  [db {move ::move/move game ::game/game}]
  (let [message (str (::player/id move) " took a turn and ended the game.")]
    (assoc db :app/game game :app/screen :game-over :app/status-message )))

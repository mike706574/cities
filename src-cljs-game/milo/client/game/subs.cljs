(ns milo.client.game.subs
  (:require [milo.game :as game]
            [milo.player :as player]
            [re-frame.core :as rf]))

(rf/reg-sub
 :db
 (fn [db _]
   db))

(rf/reg-sub
 :screen
 (fn [db _]
   (:app/screen db)))

(rf/reg-sub
 :loading?
 (fn [db _]
   (:app/loading? db)))

(rf/reg-sub
 :status-message
 (fn [db _]
   (:app/status-message db)))

(rf/reg-sub
 :move-message
 (fn [db _]
   (:app/move-message db)))

;; Game
(rf/reg-sub
 :game
 (fn [db _]
   (:app/game db)))

(rf/reg-sub
 :opponent
 (fn [{game :app/game player :app/player} _]
   (game/opponent game player)))

(rf/reg-sub
 :round-number
 (fn [{game :app/game} _]
   (::game/round-number game)))

(rf/reg-sub
 :hand
 (fn [{game :app/game player :app/player} _]
   (get-in game [::game/round ::game/player-data player ::player/hand])))

(rf/reg-sub
 :expeditions
 (fn [{game :app/game player :app/player} _]
   (get-in game [::game/round ::game/player-data player ::player/expeditions])))

(rf/reg-sub
 :opponent-expeditions
 (fn [{game :app/game player :app/player} _]
   (get-in game [::game/round ::game/player-data (game/opponent game player) ::player/expeditions])))

(rf/reg-sub
 :draw-count
 (fn [{game :app/game} _]
   (get-in game [::game/round ::game/draw-count])))

(rf/reg-sub
 :turn
 (fn [{game :app/game} _]
   (get-in game [::game/round ::game/turn])))

(rf/reg-sub
 :available-discards
 (fn [{game :app/game} _]
   (get-in game [::game/round ::game/available-discards])))

(rf/reg-sub
 :past-rounds
 (fn [{game :app/game} _]
   (::game/past-rounds game)))

(rf/reg-sub
 :player
 (fn [db _]
   (:app/player db)))

(rf/reg-sub
 :destination
 (fn [db _]
   (:app/destination db)))

(rf/reg-sub
 :card
 (fn [db _]
   (:app/card db)))

(rf/reg-sub
 :source
 (fn [db _]
   (:app/source db)))

(rf/reg-sub
 :error-message
 (fn [db _]
   (:app/error-message db)))

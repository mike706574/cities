(ns milo.client.game.subs
  (:require [milo.game :as game]
            [milo.player :as player]
            [re-frame.core :as rf]
            [taoensso.timbre :as log]))

(rf/reg-sub
 :db
 (fn [db _]
   db))

(rf/reg-sub
 :screen
 (fn [db _]
   (:screen db)))

(rf/reg-sub
 :loading?
 (fn [db _]
   (:loading? db)))

(rf/reg-sub
 :status-message
 (fn [db _]
   (:status-message db)))

(rf/reg-sub
 :move-message
 (fn [db _]
   (:move-message db)))

;; Game
(rf/reg-sub
 :game
 (fn [db _]
   (:game db)))

(rf/reg-sub
 :opponent
 (fn [{game :game player :player} _]
   (game/opponent game player)))

(rf/reg-sub
 :round-number
 (fn [{game :game} _]
   (inc (count (::game/past-rounds game)))))

(rf/reg-sub
 :last-round
 (fn [{game :game} _]
   (last (::game/past-rounds game))))

(rf/reg-sub
 :hand
 (fn [{game :game player :player} _]
   (get-in game [::game/round ::game/player-data player ::player/hand])))

(rf/reg-sub
 :expeditions
 (fn [{game :game player :player} _]
   (get-in game [::game/round ::game/player-data player ::player/expeditions])))

(rf/reg-sub
 :opponent-expeditions
 (fn [{game :game player :player} _]
   (get-in game [::game/round ::game/player-data (game/opponent game player) ::player/expeditions])))

(rf/reg-sub
 :draw-count
 (fn [{game :game} _]
   (get-in game [::game/round ::game/draw-count])))

(rf/reg-sub
 :turn
 (fn [{game :game} _]
   (get-in game [::game/round ::game/turn])))

(rf/reg-sub
 :available-discards
 (fn [{game :game} _]
   (get-in game [::game/round ::game/available-discards])))

(rf/reg-sub
 :past-rounds
 (fn [{game :game} _]
   (::game/past-rounds game)))



(rf/reg-sub
 :player
 (fn [db _]
   (:player db)))

(rf/reg-sub
 :destination
 (fn [db _]
   (:destination db)))

(rf/reg-sub
 :card
 (fn [db _]
   (:card db)))

(rf/reg-sub
 :source
 (fn [db _]
   (:source db)))

(rf/reg-sub
 :error-message
 (fn [db _]
   (:error-message db)))

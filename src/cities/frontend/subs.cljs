(ns cities.frontend.subs
  (:require [re-frame.core :as rf]
            [cities.game :as game]))

(defn game [db]
  (get-in db [:active-games (:game-id db)]))

(defn available-discards [db]
  (get-in (game db) [:cities.game/round :cities.game/available-discards]))

(defn my-turn?
  [player [_ game]]
  (= player (-> game :cities.game/round :cities.game/turn)))

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
 :ready-games
 (fn [{:keys [:player :active-games]} _]
   (filter (partial my-turn? player) active-games)))

(rf/reg-sub
 :waiting-games
 (fn [{:keys [:player :active-games]} _]
   (filter (partial (complement my-turn?) player) active-games)))

(rf/reg-sub
 :completed-games
 (fn [db _]
   (:completed-games db)))

(rf/reg-sub
 :received-invites
 (fn [{:keys [player invites]} _]
   (filter #(= (second %) player) invites)))

(rf/reg-sub
 :sent-invites
 (fn [{:keys [player invites]} _]
   (filter #(= (first %) player) invites)))

(rf/reg-sub
 :messages
 (fn [db _]
   (:messages db)))

(rf/reg-sub
 :player
 (fn [db _]
   (:player db)))

(rf/reg-sub
 :error
 (fn [db _]
   (select-keys db [:error-message :error-body])))

;; Move
(rf/reg-sub
 :card
 (fn [db _]
   (:card db)))

(rf/reg-sub
 :card-selected?
 (fn [db _]
   (not (nil? (:card db)))))

(rf/reg-sub
 :destination
 (fn [db _]
   (:destination db)))

(rf/reg-sub
 :drawn-discard
 (fn [db _]
   (when-let [source (:source db)]
     (first (filter #(= (:cities.card/color %) source) (available-discards db))))))

(rf/reg-sub
 :source
 (fn [db _]
   (:source db)))

(rf/reg-sub
 :move-message
 (fn [db _]
   (:move-message db)))

;; Game
(rf/reg-sub
 :game
 (fn [db _]
   (game db)))

(rf/reg-sub
 :opponent
 (fn [db _]
   (game/opponent (game db) (:player db))))

(rf/reg-sub
 :round-number
 (fn [db _]
   (inc (count (:cities.game/past-rounds (game db))))))

(rf/reg-sub
 :last-round
 (fn [db _]
   (last (:cities.game/past-rounds (game db)))))

(rf/reg-sub
 :hand
 (fn [{player :player :as db} _]
   (get-in (game db) [:cities.game/round
                      :cities.game/player-data
                      player
                      :cities.player/hand])))

(rf/reg-sub
 :expeditions
 (fn [db _]
   (let [game (game db)]
     (get-in game [:cities.game/round
                   :cities.game/player-data
                   (:player db)
                   :cities.player/expeditions]))))

(rf/reg-sub
 :opponent-expeditions
 (fn [db _]
   (let [game (game db)]
     (get-in game [:cities.game/round
                   :cities.game/player-data
                   (:cities.game/opponent game)
                   :cities.player/expeditions]))))

(rf/reg-sub
 :draw-count
 (fn [db _]
   (get-in (game db) [:cities.game/round :cities.game/draw-count])))

(defn turn?
  [db]
  (= (:player db) (get-in (game db) [:cities.game/round :cities.game/turn])))

(rf/reg-sub :turn? (fn [db _] (turn? db)))

(rf/reg-sub
 :turn-ready?
 (fn [db _]
   (and (turn? db) (:card db) (:destination db) (:source db))))

(rf/reg-sub
 :available-discards
 (fn [db _]
   (available-discards db)))

(rf/reg-sub
 :past-rounds
 (fn [db _]
   (:cities.game/past-rounds (game db))))

(rf/reg-sub
 :toast
 (fn [db _]
   (:toast db)))

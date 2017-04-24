(ns milo.client.menu.subs
  (:require [re-frame.core :as rf]
            [milo.game :as game]
            [milo.menu :as menu]))

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
 (fn [{:keys [:player :milo/active-games]} _]
   (filter #(= (::game/turn %) player) active-games)))

(rf/reg-sub
 :waiting-games
 (fn [{:keys [:player :milo/active-games]} _]
   (filter #(not= (::game/turn %) player) active-games)))

(rf/reg-sub
 :completed-games
 (fn [db _]
   (:milo/completed-games db)))

(rf/reg-sub
 :received-invites
 (fn [db _]
   (:milo/received-invites db)))

(rf/reg-sub
 :sent-invites
 (fn [db _]
   (:milo/sent-invites db)))

(rf/reg-sub
 :messages
 (fn [db _]
   (:messages db)))

(rf/reg-sub
 :player
 (fn [db _]
   (:player db)))

(rf/reg-sub
 :error-message
 (fn [db _]
   (:error-message db)))

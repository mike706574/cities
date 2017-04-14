(ns milo.client.menu.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 :state
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
 :games
 (fn [db _]
   (:menu/games db)))

(rf/reg-sub
 :received-invites
 (fn [db _]
   (:menu/received-invites db)))

(rf/reg-sub
 :sent-invites
 (fn [db _]
   (:menu/sent-invites db)))

(rf/reg-sub
 :messages
 (fn [db _]
   (:menu/messages db)))

(rf/reg-sub
 :player
 (fn [db _]
   (:app/player db)))

(rf/reg-sub
 :error-message
 (fn [db _]
   (:app/error-message db)))

(ns misplaced-villages.client.menu.message
  (:require [misplaced-villages.game :as game]
            [taoensso.timbre :as log]))

(defmulti handle
  (fn [db message]
    (log/debug "Received message:" message)
    (:menu/status message)))

(defmethod handle :state
  [db message]
  (log/info "Setting state!")
  (merge db (:menu/state message)))

(defmethod handle :received-invite
  [db {invite :menu/invite}]
  (-> db
      (update :menu/received-invites conj invite)
      (update :menu/messages conj (str (first invite) " invited you to play!"))))

(defmethod handle :invite-rejected
  [db {invite :menu/invite}]
  (-> db
      (update :menu/sent-invites disj invite)
      (update :menu/messages conj (str (second invite) " rejected your invite!"))))

(defmethod handle :invite-canceled
  [db {invite :menu/invite}]
  (-> db
      (update :menu/received-invites disj invite)
      (update :menu/messages conj (str (first invite) " canceled your invite!"))))

(defmethod handle :sent-invite
  [db {invite :menu/invite}]
  (-> db
      (update :menu/messages conj (str "Invite sent to " (second invite) "!"))
      (update :menu/sent-invites conj invite)))

(defmethod handle :rejected-invite
  [db {invite :menu/invite}]
  (update db :menu/messages conj (str "You rejected "(first invite) "'s invite!")))

(defmethod handle :already-invited
  [db {invite :menu/invite}]
  (update db :menu/messages conj (str "You have already invited " (second invite) " to play.")))

(defmethod handle :already-invited-by
  [db {invite :menu/invite}]
  (update db :menu/messages conj (str "You currently have an invite from " (first invite) ".")))

(defmethod handle :no-invite-to-reject
  [db {invite :menu/invite}]
  (update db :menu/messages conj (str "You tried to reject an invite from " (first invite) ", but we couldn't find it.")))

(defmethod handle :no-invite-to-cancel
  [db {invite :menu/invite}]
  (update db :menu/messages conj (str "You tried to cancel an invite for " (second invite) ", but we couldn't find it.")))

(defmethod handle :canceled-invite
  [db {invite :menu/invite}]
  (update db :menu/messages conj (str "You canceled your invite for " (second invite) ".")))

(defmethod handle :game-created
  [db {game :menu/game}]
  (let [{:keys [::game/id ::game/opponent ::game/turn ::game/round-number]} game]
    (-> db
        (update :menu/games assoc id game)
        (update :menu/messages conj (str "Game " id " created against " (::game/opponent game) ".")))))

(defmethod handle :error
  [db {error-message :menu/error-message}]
  (assoc db :app/screen :error :app/error-message :error-message))

(ns milo.api.invite
  (:require [clojure.spec :as s]
            [com.stuartsierra.component :as component]
            [milo.api.event :as event]
            [milo.game :as game]))

(s/def :milo/invite (s/cat :sender :milo.player/id :recipient :milo.player/id))

(defprotocol InviteManager
  "Manages games and invites."
  (invites-for-player [this player-id])
  (send-invite [this invite])
  (delete-invite [this invite])
  (accept-invite [this invite]))

(defn- invite-already-exists
  [invites invite]
  (let [[sender recipient] invite]
    (loop [[head & tail] invites]
      (when head
        (cond
          (= head [sender recipient]) {:milo/status :invite-already-sent}
          (= head [recipient sender]) {:milo/status :invite-already-received}
          :else (recur tail))))))

(defrecord RefInviteManager [games invites event-manager]
  InviteManager
  (invites-for-player [this player-id]
    (filter #(some #{player-id} %) @invites))

  (send-invite [this invite]
    (dosync
     (or (invite-already-exists @invites invite)
         (let [event (event/store event-manager {:milo/status :sent-invite
                                                 :milo/invite invite})]
           (alter invites conj invite)
           event))))

  (delete-invite [this invite]
    (dosync
     (if-not (contains? @invites invite)
       {:milo/status :invite-not-found
        :milo/invite invite}
       (let [event (event/store event-manager {:milo/status :invite-deleted
                                               :milo/invite invite})]
         (alter invites disj invite)
         event))))

  (accept-invite [this invite]
    (dosync
     (if-not (contains? @invites invite)
       {:milo/status :invite-not-found}
       (let [id (str (inc (count @games)))
             game (assoc (game/rand-game invite 4) ::game/id id)
             event (event/store event-manager {:milo/status :game-created
                                               ::game/game game
                                               :milo/invite invite})]
         (alter invites disj invite)
         (alter games assoc id game)
         event)))))

(defn manager []
  (component/using (map->RefInviteManager {})
    [:games :invites :event-manager]))

(ns cities.backend.invite
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [cities.backend.event :as event]
            [cities.game :as game]))

(s/def :cities/invite (s/cat :sender :cities.player/id :recipient :cities.player/id))

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
          (= head [sender recipient]) {:cities/status :invite-already-sent
                                       :cities/invite invite}
          (= head [recipient sender]) {:cities/status :invite-already-received
                                       :cities/invite invite}
          :else (recur tail))))))

(defrecord RefInviteManager [active-games invites event-manager]
  InviteManager
  (invites-for-player [this player-id]
    (into #{} (filter #(some #{player-id} %) @invites)))

  (send-invite [this invite]
    (dosync
     (or (invite-already-exists @invites invite)
         (let [event (event/store event-manager {:cities/status :sent-invite
                                                 :cities/invite invite})]
           (alter invites conj invite)
           event))))

  (delete-invite [this invite]
    (dosync
     (if-not (contains? @invites invite)
       {:cities/status :invite-not-found
        :cities/invite invite}
       (let [event (event/store event-manager {:cities/status :invite-deleted
                                               :cities/invite invite})]
         (alter invites disj invite)
         event))))

  (accept-invite [this invite]
    (dosync
     (if-not (contains? @invites invite)
       {:cities/status :invite-not-found}
       (let [id (str (inc (count @active-games)))
             game (assoc (game/rand-game invite) ::game/id id)
             event (event/store event-manager {:cities/status :game-created
                                               ::game/game game
                                               :cities/invite invite})]
         (alter invites disj invite)
         (alter active-games assoc id game)
         event)))))

(defn manager []
  (component/using (map->RefInviteManager {})
    [:active-games :invites :event-manager]))

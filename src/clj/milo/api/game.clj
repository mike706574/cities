(ns milo.api.game
  (:require [com.stuartsierra.component :as component]
            [milo.api.event :as event]
            [milo.game :as game]))

(defprotocol GameManager
  "Manages games and invites."
  (game-for-id [this game-id])
  (games-for-player [this player])
  (take-turn [this game-id move]))

(defn- invite-already-exists
  [invites invite]
  (let [[sender recipient] invite]
    (loop [[head & tail] invites]
      (when head
        (cond
          (= head [sender recipient]) {:milo/status :invite-already-sent}
          (= head [recipient sender]) {:milo/status :invite-already-received}
          :else (recur tail))))))

(defmacro if-not-let
  ([bindings then]
   `(if-let ~bindings ~then nil))
  ([bindings then else]
   (let [form (bindings 0) tst (bindings 1)]
     `(let [temp# ~tst]
        (if-not temp#
          ~then
          (let [~form temp#]
            ~else))))))

(defrecord RefGameManager [games event-manager]
  GameManager
  (game-for-id [this game-id]
    (get @games game-id))

  (games-for-player [this player-id]
    (into {} (filter #(some #{player-id} (::game/players (val %))) @games)))

  (take-turn [this game-id move]
    (dosync
     (if-not-let [game (get @games game-id)]
       {:milo/status :game-not-found}
       (let [{status :milo.game/status
              game' :milo.game/game} (game/take-turn game move)]
         (if-not (contains? #{:taken :round-over :game-over} status)
           {:milo/status status :milo.game/move move :milo.game/id game-id}
           (let [event (event/store event-manager {:milo/status status
                                                   :milo.game/move move
                                                   :milo.game/id game-id
                                                   :milo.game/game game'})]
             (alter games (fn [games] (assoc games game-id game')))
             event)))))))

(defn manager []
  (component/using (map->RefGameManager {})
    [:games :event-manager]))

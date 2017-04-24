(ns milo.server.game-resource
  (:require [aleph.http :as http]
            [clojure.spec :as spec]
            [manifold.bus :as bus]
            [manifold.stream :as stream]
            [manifold.deferred :as d]
            [milo.game :as game]
            [milo.move :as move]
            [milo.card :as card]
            [milo.player :as player]
            [milo.server.connection :as conn]
            [milo.server.event :as event]
            [milo.server.http :refer [body-response
                                      handle-exceptions
                                      with-body
                                      non-websocket-response]]
            [milo.server.message :refer [decode
                                         encode]]
            [milo.server.model :as model]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

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

(defn turn-taken?
  [status]
  (contains? #{:taken :round-over :game-over} status))

(defn take-turn
  [{:keys [event-manager games] :as deps} player game-id move]
  (dosync
   (if-not-let [game (get @games game-id)]
     {:milo/status :game-not-found}
     (let [{status :milo.game/status
            game' :milo.game/game} (game/take-turn game move)]
       (if-not (turn-taken? status)
         {:milo/status status}
         (let [event (event/store event-manager {:milo/status status
                                                 :milo.move/move move
                                                 :milo.game/game game'})]
           (alter games (fn [games] (assoc games game-id game')))
           event))))))

(defn handle-turn
  [{:keys [player-bus game-bus] :as deps} request]
  (handle-exceptions request
    (with-body [move :milo.move/move request]
      (let [{{game-id :id} :params
             {player "player"} :headers} request]
        (if-not (= player (:milo.player/id move))
          (body-response 403 request {:milo.server/message "Taking turns for other players is not allowed."})
          (let [{status :milo/status :as response} (take-turn deps player game-id move)]
            (cond
              (turn-taken? status) (let [opponent (game/opponent (:milo.game/game response) player)]
                                     (bus/publish! player-bus player response)
                                     (bus/publish! player-bus opponent response)
                                     (body-response 200 request response))
              (= status :game-not-found) (body-response 404 request {:milo/status :game-not-found
                                                                     :milo.game/id game-id})
              :else (body-response 409 request {:milo/status :turn-not-taken
                                                :milo.game/status status
                                                :milo.move/move move}))))))))

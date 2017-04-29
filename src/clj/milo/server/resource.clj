(ns milo.server.resource
  (:require [aleph.http :as http]
            [clojure.spec :as spec]
            [clojure.spec.test :as stest]
            [clojure.string :as str]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [milo.card :as card]
            [milo.game :as game]
            [milo.menu :as menu]
            [milo.move :as move]
            [milo.score :as score]
            [milo.player :as player]
            [milo.server.connection :as conn]
            [milo.server.event :as event]
            [milo.server.http :refer [with-body
                                      handle-exceptions
                                      body-response
                                      not-acceptable
                                      parsed-body
                                      unsupported-media-type]]
            [milo.server.message :refer [encode decode]]
            [milo.server.model :as model]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(spec/def ::invite (spec/cat :sender :milo.player/id :receiver :milo.player/id))

(defn invite-already-exists
  [invites invite]
  (let [[sender recipient] invite]
    (loop [[head & tail] invites]
      (when head
        (cond
          (= head [sender recipient]) {:milo/status :invite-already-sent}
          (= head [recipient sender]) {:milo/status :invite-already-received}
          :else (recur tail))))))

(defn send-invite
  [{:keys [event-manager invites]} invite]
  (dosync
   (or (invite-already-exists @invites invite)
       (let [event (event/store event-manager {:milo/status :sent-invite
                                               :milo/invite invite})]
         (alter invites conj invite)
         event))))

(defn handle-sending-invite
  [{:keys [player-bus users] :as deps} request]
  (handle-exceptions request
    (with-body [invite ::invite request]
      (let [[sender recipient] invite
            player (get-in request [:headers "player"])]
        (cond
          (not (= player sender)) (body-response 403 request {:milo.server/message "Sending invites for other players is not allowed."})
          (= sender recipient) (body-response 409 request {:milo.server/message "You can't invite yourself."})
          (not (contains? users recipient)) (body-response 409 request {:milo.server/message (str "Player " recipient " does not exist.")})
          :else
          (let [{status :milo/status event-id :milo/event-id :as response} (send-invite deps invite)]
            (if (contains? #{:invite-already-sent :invite-already-received} status)
              (body-response 409 request response)
              (do (log/debug (str "[#" event-id "] Sent invite " invite))
                  (bus/publish! player-bus sender response)
                  (bus/publish! player-bus recipient (assoc response :milo/status :received-invite))
                  (body-response 201 request response)))))))))

(defn delete-invite
  [{:keys [event-manager invites]} invite]
  (dosync
   (if-not (contains? @invites invite)
     {:milo/status :invite-not-found
      :milo/invite invite}
     (let [event (event/store event-manager {:milo/status :invite-deleted
                                             :milo/invite invite})]
       (alter invites disj invite)
       event))))

(defn handle-deleting-invite
  [{:keys [player-bus] :as deps} request]
  (handle-exceptions request
    (let [{{player "player"} :headers
           {sender :sender recipient :recipient} :params} request
          invite [sender recipient]
          sender? (= player sender)]
      (if-not (or sender? (= player recipient))
        (body-response 403 request {:milo.server/message "You can't delete other players' invites."})
        (let [{status :milo/status :as response} (delete-invite deps invite)]
          (if (= status :invite-not-found)
            (body-response 404 request {:milo/status :invite-not-found
                                        :milo/invite invite})
            (let [sender-message (assoc response :milo/status (if sender?
                                                                :sent-invite-canceled
                                                                :sent-invite-rejected))
                  recipient-message (assoc response :milo/status (if sender?
                                                                   :received-invite-canceled
                                                                   :received-invite-rejected))]
              (bus/publish! player-bus sender sender-message)
              (bus/publish! player-bus recipient recipient-message)
              (body-response 200 request (if sender? sender-message recipient-message)))))))))

(defn accept-invite
  [{:keys [event-manager games invites]} invite]
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
       event))))

(defn handle-accepting-invite
  [{:keys [player-bus] :as deps} request]
  (handle-exceptions request
    (with-body [invite ::invite request]
      (let [{player "player"} (:headers request)
            [sender recipient] invite]
        (if-not (= player recipient)
          (body-response 403 request {:milo.server/message "You can't accept invites for other players."})
          (let [{status :milo/status :as response} (accept-invite deps invite)]
            (if (= status :invite-not-found)
              (body-response 409 request response)
              (let [sender-message (update response ::game/game (partial model/game-summary-for sender))
                    recipient-message (update response ::game/game (partial model/game-summary-for recipient))]
                (bus/publish! player-bus sender sender-message)
                (bus/publish! player-bus recipient recipient-message)
                (body-response 201 request recipient-message)))))))))

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
         {:milo/status status :milo.move/move move :milo.game/id game-id}
         (let [event (event/store event-manager {:milo/status status
                                                 :milo.move/move move
                                                 :milo.game/id game-id
                                                 :milo.game/game game'})]
           (alter games (fn [games] (assoc games game-id game')))
           event))))))

(defn handle-turn
  [{:keys [player-bus] :as deps} request]
  (handle-exceptions request
    (with-body [move :milo.move/move request]
      (let [{{game-id :id} :params
             {player "player"} :headers} request]
        (if-not (= player (:milo.player/id move))
          (body-response 403 request {:milo.server/message "Taking turns for other players is not allowed."})
          (let [{status :milo/status :as response} (take-turn deps player game-id move)]
            (cond
              (turn-taken? status) (let [opponent (game/opponent (:milo.game/game response) player)
                                         player-message (update response :milo.game/game #(model/game-for player %))
                                         opponent-message (update response :milo.game/game #(model/game-for opponent %))]
                                     (bus/publish! player-bus player player-message)
                                     (bus/publish! player-bus opponent opponent-message)
                                     (body-response 200 request player-message))
              (= status :game-not-found) (body-response 404 request {:milo/status :game-not-found
                                                                     :milo.game/id game-id})
              :else (body-response 409 request {:milo/status :turn-not-taken
                                                :milo.game/id game-id
                                                :milo.game/status status
                                                :milo.move/move move}))))))))

(defn handle-game-retrieval
  [{:keys [games] :as deps} request]
  (handle-exceptions request
    (or (not-acceptable request)
        (let [{{game-id :id} :params
               {player "player"} :headers} request]
          (if-let [game (get @games game-id)]
            (body-response 200 request {:milo/status :game-found
                                        :milo.game/id game-id
                                        :milo.game/game (model/game-for player game)} )
            (body-response 404 request {:milo/status :game-not-found
                                        :milo.game/id game-id}))))))

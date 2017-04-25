(ns milo.server.menu-resource
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
                                      non-websocket-response
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
  [{:keys [player-bus] :as deps} request]
  (handle-exceptions request
    (with-body [invite ::invite request]
      (let [[sender recipient] invite
            player (get-in request [:headers "player"])]
        (if-not (= player sender)
          (body-response 403 request {:milo.server/message "Sending invites for other players is not allowed."})
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

(defn handle
  [{:keys [games invites game-bus player-bus conn-manager] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (constantly nil))]
    (if-not conn
      (non-websocket-response)
      (let [conn-id (conn/add! conn-manager :menu conn)
            conn-label (str "[ws-conn-" conn-id "] ")]
        (log/debug (str conn-label "Initial connection established."))
        (d/let-flow [initial-message @(s/take! conn)]
          (try
            (let [player (decode initial-message)]
              (log/debug (str conn-label "Connecting player \"" player "\"."))
              ;; Give player current menu state
              (let [state (model/menu-for player @games @invites)
                    state-message (assoc state :milo/status :state)]
                (s/put! conn (encode state-message)))
              ;; Player updates
              (s/connect-via
               (bus/subscribe player-bus player)
               (fn [message]
                 (log/trace (str conn-label "Preparing message."))
                 (s/put! conn (encode message)))
               conn)
              (log/debug (str conn-label "Connected player \"" player "\".")))
            {:status 101}
            (catch Exception e
              (log/error e (str conn-label "Exception thrown while connecting player. Initial message: " initial-message))
              {:status 500})))))))

(defn websocket-handler
  [deps]
  (partial handle deps))

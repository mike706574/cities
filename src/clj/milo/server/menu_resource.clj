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

;; REST-y functions, to replace websocket messages
;; (defn handle-turn
;;   [{:keys [player-bus game-bus] :as deps} request]

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
  [{:keys [event-id invites]} invite]
  (dosync
   (or (invite-already-exists @invites invite)
       (let [event-id (alter event-id inc)]
         (alter invites conj invite)
         {:milo/event-id event-id
          :milo/status :sent-invite
          :milo.menu/invite invite}))))

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
                  (println sender ",,,," recipient)
                  (bus/publish! player-bus sender response)
                  (bus/publish! player-bus recipient (assoc response :milo/status :received-invite))
                  (body-response 201 request response)))))))))


(defn delete-invite
  [{:keys [event-id invites]} invite]
  (dosync
   (if-not (contains? @invites invite)
     {:milo/status :invite-not-found
      :milo.menu/invite invite}
     (let [event-id (alter event-id inc)]
       (alter invites disj invite)
       {:milo/status :invite-deleted
        :milo/event-id event-id
        :milo.menu/invite invite}))))

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
                                        :milo.menu/invite invite})
            (let [sender-message (assoc response :milo/status (if sender?
                                                                :sent-invite-canceled
                                                                :sent-invite-rejected))
                  recipient-message (assoc response :milo/status (if sender?
                                                                   :received-invite-cancelled
                                                                   :received-invite-rejected))]
              (bus/publish! player-bus sender sender-message)
              (bus/publish! player-bus recipient recipient-message)
              (body-response 200 request recipient-message))))))))

(defn accept-invite
  [{:keys [event-id games invites]} invite]
  (println "INVINV" invites)
  (dosync
   (if-not (contains? @invites invite)
     {:milo/status :invite-not-found}
     (let [event-id (alter event-id inc)
           id (str (inc (count @games)))
           game (assoc (game/rand-game invite 4) :milo.game/id id)]
       (alter invites disj invite)
       (alter games assoc id game)
       {:milo/event-id event-id
        :milo/status :game-created
        :milo.menu/game game}))))

(defn handle-accepting-invite
  [{:keys [player-bus] :as deps} request]
  (handle-exceptions request
    (with-body [invite ::invite request]
      (let [{player "player"} (:headers request)
            [sender recipient] invite]
        (if-not (= player recipient)
          (body-response 404 request {:milo.server/message "You can't accept invites for other players."})
          (let [{status :milo/status :as response} (accept-invite deps invite)]
            (if (= status :invite-not-found)
              (body-response 409 request response)
              (let [sender-message (update response :milo.game/game (partial :game/game-summary-for sender))
                    recipient-message (update response :milo.game/game (partial :game/game-summary-for recipient))]
                (bus/publish! player-bus sender sender-message)
                (bus/publish! player-bus recipient recipient-message)
                (body-response 201 request response)))))))))

(defmulti process-message
  "Given a message, return all actions to be performed."
  (fn [invites player message]
    (::menu/status message)))

(defmethod process-message :send-invite
  [{invites :invites} player {opponent ::menu/player}]
  (let [to-invite [player opponent]
        from-invite [opponent player]]
    (cond
      (contains? invites to-invite) [[:publish player {::menu/status :already-invited
                                                       ::menu/invite to-invite}]]
      (contains? invites from-invite) [[:publish player {::menu/status :already-invited-by
                                                         ::menu/invite from-invite}]]
      ;; Send invite
      :else [[:publish player {::menu/status :sent-invite ::menu/invite to-invite}]
             [:publish opponent {::menu/status :received-invite ::menu/invite to-invite}]
             [:add-invite to-invite]])))

(defmethod process-message :reject-invite
  [{invites :invites} player {opponent ::menu/player}]
  (let [invite [opponent player]]
    (if (contains? invites invite)
      ;; Reject invite
      [[:publish player {::menu/status :rejected-invite ::menu/invite invite}]
       [:publish opponent {::menu/status :invite-rejected ::menu/invite invite}]
       [:remove-invite invite]]
      ;; Invite not found
      [[:publish player {::menu/status :no-invite-to-reject ::menu/invite invite}]])))

(defmethod process-message :accept-invite
  [{invites :invites} player {opponent ::menu/player}]
  (let [invite [opponent player]]
    (if-not (contains? invites invite)
      ;; Invite not found
      [[:publish player {::menu/status :no-invite-to-accept
                         ::menu/invite invite}]]
      ;; Invite found - create a game
      [[:create-game invite]])))

(defmethod process-message :cancel-invite
  [{invites :invites} player {opponent ::menu/player}]
  (let [invite [player opponent]]
    (if-not (contains? invites invite)
      ;; Invite not found
      [[:publish player {::menu/status :no-invite-to-cancel
                         ::menu/invite invite}]]
      ;; Invite found - cancel it
      [[:publish player {::menu/status :canceled-invite
                         ::menu/invite invite}]
       [:publish opponent {::menu/status :invite-canceled
                           ::menu/invite invite}]
       [:remove-invite invite]])))

(defmethod process-message :sync
  [{games :games invites :invites} player _]
  (let [menu-data (model/menu-for player @games @invites)]
    [[:publish player {::menu/status :state ::menu/state menu-data}]]))

(defmulti perform-action
  "Given an action, perform it."
  (fn [deps [action-id :as action]]
    action-id))

(defmethod perform-action :publish
  [{player-bus :player-bus} [_ player message]]
  (bus/publish! player-bus player message))

(defmethod perform-action :create-game
  [{games :games invites :invites player-bus :player-bus} [_ invite]]
)

(defmethod perform-action :add-invite
  [{invites :invites} [_ invite]]
  (alter invites conj invite))

(defmethod perform-action :remove-invite
  [{invites :invites} [_ invite]]
  (alter invites disj invite))

(defn consume-message
  [{:keys [games invites player-bus] :as deps} player raw-message]
  (let [message-id (util/uuid)]
    (log/trace (str "Consuming message " message-id) ".")
    (try
      (let [parsed-message (decode raw-message)]
        (log/trace (str "Parsed message: " parsed-message))
        (dosync
         (let [actions (process-message {:games @games
                                         :invites @invites} player parsed-message)]
           (log/trace (str "Performing " (count actions) " actions."))
           (doseq [action actions]
             (log/trace (str "Performing action: " action))
             (perform-action deps action)))))
      (log/debug (str "Successfully consumed message " message-id "."))
      (catch Exception ex
        (log/error "Exception thrown while consuming message " message-id ".")
        (log/error ex)
        (bus/publish! player-bus player {::menu/status :error
                                         ::menu/error-message (.getMessage ex)})))))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn handle
  [{:keys [games invites game-bus player-bus conn-manager] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [player (decode @(s/take! conn)) ;; TODO: Timeout and error handling.
                   state (model/menu-for player @games @invites)
                   state-message (assoc state ::menu/status :state)
                   conn-id (conn/add! conn-manager player :menu conn)]
        (log/debug (str "Player " player " connected to menu [" conn-id "]"))
        ;; Give player current menu state
        (s/put! conn (encode state-message))
        ;; Player updates
        (s/connect-via
         (bus/subscribe player-bus player)
         (fn [message]
           (println "HELLO??")
           (log/debug (str "Preparing player message for " player " [" conn-id "]"))
           (log/trace "Message:" message)
           (s/put! conn (encode message)))
         conn)
        ;; Consume messages from player
        (s/consume (partial consume-message deps player) conn)
        (log/debug (str "Player " player " ("
                        (count (::menu/games state)) " games, "
                        (count (::menu/sent-invites state)) " sent invites, "
                        (count (::menu/received-invites state)) " received invites) connected [" conn-id "]"))
        {:status 101}))))

(defn handler
  [deps]
  (partial handle deps))

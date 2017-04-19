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
            [milo.server.http :refer [body-response
                                      non-websocket-response
                                      not-acceptable
                                      parsed-body
                                      unsupported-media-type]]
            [milo.server.message :refer [encode decode]]
            [milo.server.model :as model]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

;; REST-y functions, to replace websocket messages
(defn send-invite
  [{:keys [event-id invites player-bus]} request]
  (dosync
   (let [player (get-in request [:headers "player"])
         opponent (::menu/opponent (parsed-body request))
         to-invite [player opponent]
         from-invite [opponent player]]
     (cond
       (contains? @invites to-invite) (body-response 409 request {::menu/status :already-invited})
       (contains? @invites from-invite) (body-response 409 request {::menu/status :already-invited-by})
       :else (let [event-id (swap! event-id inc)
                   body {:milo/event-id event-id
                             ::menu/status :sent-invite
                             ::menu/invite to-invite}]
               (alter invites conj to-invite)
               (bus/publish! player-bus player body)
               (bus/publish! player-bus opponent {:milo/event-id event-id
                                                  ::menu/status :received-invite
                                                  ::menu/invite to-invite})
               (body-response 201 request body))))))

(defn delete-invite
  [{:keys [event-id invites player-bus]} player request opponent]
  (dosync
   (let [player (get-in request [:headers :player])
         opponent (get-in request )
         to-invite [opponent player]
         from-invite [player opponent]]
     (cond
       (contains? invites to-invite) (let [event-id (swap! event-id inc)
                                           body {:milo/event-id event-id
                                                 ::menu/status :canceled-invite
                                                 ::menu/invite to-invite}]
                                       (alter invites disj to-invite)
                                       (bus/publish! player-bus player body)
                                       (bus/publish! player-bus opponent {:milo/event-id event-id
                                                                          ::menu/status :invite-canceled
                                                                          ::menu/invite to-invite})
                                       (body-response 202 request body))

       (contains? invites from-invite) (let [event-id (swap! event-id inc)
                                             body {:milo/event-id event-id
                                                   ::menu/status :rejected-invite
                                                   ::menu/invite from-invite}]
                                         (alter invites disj from-invite)
                                         (bus/publish! player-bus player body)
                                         (bus/publish! player-bus opponent {:milo/event-id event-id
                                                                            ::menu/status :invite-rejected
                                                                            ::menu/invite from-invite})
                                         (body-response 202 request body))
       :else (body-response 404 request {::menu/status :invite-not-found
                                         ::menu/opponent opponent})))))

(defn accept-invite
  [{:keys [event-id games invites player-bus]} player request opponent]
  (or (unsupported-media-type request)
      (not-acceptable request)
      (let [player (get-in request [:headers "player"])
            opponent (::menu/opponent (parsed-body request))
            invite [opponent player]]
        (dosync
         (if-not (contains? invites invite)
           (body-response 404 request {::menu/status :invite-not-found
                                       ::menu/invite invite})
           (let [event-id (swap! event-id inc)
                 id (str (inc (count @games)))
                 game (assoc (game/rand-game invite 4) ::game/id id)
                 body {:milo/event-id event-id
                       ::menu/status :game-created
                       ::menu/game (model/game-summary-for player game)}]
             (alter invites disj invite)
             (alter games assoc id game)
             (bus/publish! player-bus player body)
             (bus/publish! player-bus opponent {:milo/event-id event-id
                                                ::menu/status :game-created
                                                ::menu/game (model/game-summary-for opponent game)})
             (body-response 201 request body)))))))

(defn cancel-invite
  [{:keys [event-id games invites player-bus]} player request opponent]
  (dosync
   (let [invite [player opponent]]
     (if-not (contains? invites invite)
       (body-response 409 request {::menu/status :no-invite-to-cancel
                                   ::menu/invite invite})
))))

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

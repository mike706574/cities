(ns milo.server.api.resource
  (:require [manifold.bus :as bus]
            [milo.api.game :as game-api]
            [milo.api.invite :as invite-api]
            [milo.api.user :as user-api]
            [milo.game :as game]
            [milo.server.http :refer [with-body
                                      handle-exceptions
                                      body-response
                                      not-acceptable
                                      parsed-body
                                      unsupported-media-type]]
            [milo.server.api.model :as model]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(defn handle-sending-invite
  [{:keys [player-bus user-manager invite-manager]} request]
  (handle-exceptions request
    (with-body [invite :milo/invite request]
      (let [[sender recipient] invite
            player (get-in request [:headers "player"])]
        (cond
          (not (= player sender)) (body-response 403 request {:milo.server/message "Sending invites for other players is not allowed."})
          (= sender recipient) (body-response 409 request {:milo.server/message "You can't invite yourself."})
          (not (user-api/credentials user-manager recipient)) (body-response 409 request {:milo.server/message (str "Player " recipient " does not exist.")})
          :else
          (let [{status :milo/status event-id :milo/event-id :as response} (invite-api/send-invite invite-manager invite)]
            (if-not (= status :sent-invite)
              (body-response 409 request response)
              (do (log/debug (str "[#" event-id "] Sent invite " invite))
                  (bus/publish! player-bus sender response)
                  (bus/publish! player-bus recipient (assoc response :milo/status :received-invite))
                  (body-response 201 request response)))))))))

(defn handle-deleting-invite
  [{:keys [player-bus invite-manager]} request]
  (handle-exceptions request
    (let [{{player "player"} :headers
           {sender :sender recipient :recipient} :params} request
          invite [sender recipient]
          sender? (= player sender)]
      (if-not (or sender? (= player recipient))
        (body-response 403 request {:milo.server/message "You can't delete other players' invites."})
        (let [{status :milo/status :as response} (invite-api/delete-invite invite-manager invite)]
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

(defn handle-accepting-invite
  [{:keys [player-bus invite-manager]} request]
  (handle-exceptions request
    (with-body [invite :milo/invite request]
      (let [{player "player"} (:headers request)
            [sender recipient] invite]
        (if-not (= player recipient)
          (body-response 403 request {:milo.server/message "You can't accept invites for other players."})
          (let [{status :milo/status :as response} (invite-api/accept-invite invite-manager invite)]
            (if (= status :invite-not-found)
              (body-response 409 request response)
              (let [sender-message (update response ::game/game (partial model/summarize-game-for sender))
                    recipient-message (update response ::game/game (partial model/summarize-game-for recipient))]
                (bus/publish! player-bus sender sender-message)
                (bus/publish! player-bus recipient recipient-message)
                (body-response 201 request recipient-message)))))))))

(defn turn-taken?
  [status]
  (contains? #{:taken :round-over :game-over} status))

(defn handle-turn
  [{:keys [player-bus game-manager]} request]
  (handle-exceptions request
    (with-body [move :milo.game/move request]
      (let [{{game-id :id} :params
             {player "player"} :headers} request]
        (if-not (= player (:milo.player/id move))
          (body-response 403 request {:milo.server/message "Taking turns for other players is not allowed."})
          (let [{status :milo/status :as response} (game-api/take-turn game-manager game-id move)]
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
                                                :milo.game/move move}))))))))

(defn handle-game-retrieval
  [{:keys [game-manager]} request]
  (handle-exceptions request
    (or (not-acceptable request)
        (let [{{game-id :id} :params
               {player "player"} :headers} request]
          (if-let [game (game-api/game-for-id game-manager game-id)]
            (body-response 200 request {:milo/status :game-found
                                        :milo.game/id game-id
                                        :milo.game/game (model/game-for player game)} )
            (body-response 404 request {:milo/status :game-not-found
                                        :milo.game/id game-id}))))))

(defn separate-invites-by-direction
  [player invites]
  (loop [[head & tail] invites
         out [#{} #{}]]
    (if head
      (recur tail (update out (if (= (first head) player) 0 1) conj head))
      out)))

(defn handle-menu-retrieval
  [{:keys [game-manager invite-manager user-manager]} request]
  (handle-exceptions request
    (let [player (get-in request [:headers "player"])
          active-games (->> player
                            (game-api/active-games-for-player game-manager)
                            (util/map-vals (partial model/summarize-game-for player)))
          invites (invite-api/invites-for-player invite-manager player)

          usernames (-> #{}
                    (into (mapcat identity invites))
                    (into (map (comp :milo.game/opponent val)) active-games))
          avatars (into {} (map
                            (fn [username]
                              [username (user-api/avatar user-manager username)])
                            usernames))
          menu {:milo.player/id player
                :milo/active-games active-games
                :milo/avatars avatars
                :milo/invites invites}]
      (body-response 200 request menu))))

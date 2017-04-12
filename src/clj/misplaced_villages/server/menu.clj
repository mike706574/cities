(ns misplaced-villages.server.menu
  (:require [aleph.http :as http]
            [clojure.edn :as edn]
            [clojure.spec :as spec]
            [clojure.spec.test :as stest]
            [clojure.string :as str]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [misplaced-villages.card :as card]
            [misplaced-villages.game :as game]
            [misplaced-villages.move :as move]
            [misplaced-villages.score :as score]
            [misplaced-villages.player :as player]
            [taoensso.timbre :as log]))

(defn games-for
  [games player]
  (letfn [(playing? [[id state]]
            (some #{player} (::game/players state)))
          (transform [[id state]]
            (let [{:keys [::game/past-rounds
                          ::game/players
                          ::game/round]} state]
              [id {::game/id id
                   ::game/round-number (count past-rounds)
                   ::game/opponent (first (filter #(not= % player) players))
                   ::game/turn (::game/turn round)}]))]
    (into {} (comp (filter playing?)
                   (map transform))
          games)))

(defn filter-and-divide
  [f g coll]
  (loop [[head & tail] coll
         out ['() '()]]
    (if head
      (if (f head)
        (recur tail (update out (if (g head) 0 1) conj head))
        (recur tail out))
      out)))

(defn invites-for
  [invites player]
  (map set (filter-and-divide
            #(some #{player} %)
            #(= (first %) player)
            invites)))

(defn state-for
  [games invites player]
  (let [games-for-player (games-for games player)
        [sent-invites
         received-invites] (invites-for invites player)]
    {:menu/games games-for-player
     :menu/sent-invites sent-invites
     :menu/received-invites received-invites}))

(defmulti process-message
  "Given a message, return all actions to be performed."
  (fn [invites player message]
    (:menu/status message)))

(defmethod process-message :send-invite
  [{invites :invites} player {opponent :menu/player}]
  (let [to-invite [player opponent]
        from-invite [opponent player]]
    (cond
      (contains? invites to-invite) [[:publish player {:menu/status :already-invited
                                                       :menu/invite to-invite}]]
      (contains? invites from-invite) [[:publish player {:menu/status :already-invited-by
                                                         :menu/invite from-invite}]]
      ;; Send invite
      :else [[:publish player {:menu/status :sent-invite :menu/invite to-invite}]
             [:publish opponent {:menu/status :received-invite :menu/invite to-invite}]
             [:add-invite to-invite]])))

(defmethod process-message :reject-invite
  [{invites :invites} player {opponent :menu/player}]
  (let [invite [opponent player]]
    (if (contains? invites invite)
      ;; Reject invite
      [[:publish player {:menu/status :rejected-invite :menu/invite invite}]
       [:publish opponent {:menu/status :invite-rejected :menu/invite invite}]
       [:remove-invite invite]]
      ;; Invite not found
      [[:publish player {:menu/status :no-invite-to-reject :menu/invite invite}]])))

(defmethod process-message :accept-invite
  [{invites :invites} player {opponent :menu/player}]
  (let [invite [opponent player]]
    (if-not (contains? invites invite)
      ;; Invite not found
      [[:publish player {:menu/status :no-invite-to-accept
                         :menu/invite invite}]]
      ;; Invite found - create a game
      [[:create-game invite]
       [:remove-invite invite]])))

(defmethod process-message :cancel-invite
  [{invites :invites} player {opponent :menu/player}]
  (let [invite [player opponent]]
    (if-not (contains? invites invite)
      ;; Invite not found
      [[:publish player {:menu/status :no-invite-to-cancel
                         :menu/invite invite}]]
      ;; Invite found - cancel it
      [[:publish player {:menu/status :canceled-invite
                         :menu/invite invite}]
       [:publish opponent {:menu/status :invite-canceled
                           :menu/invite invite}]
       [:remove-invite invite]])))

(defmethod process-message :sync
  [{games :games invites :invites} player _]
  [[:publish player {:menu/status :state :menu/state (state-for games invites player)}]])

(defmulti perform-action
  "Given an action, perform it."
  (fn [deps [action-id :as action]]
    (log/debug (str "Performing action: " action))
    action-id))

(defmethod perform-action :publish
  [{player-bus :player-bus} [_ player message]]
  (bus/publish! player-bus player message))

(defmethod perform-action :create-game
  [{games :games player-bus :player-bus} [_ invite]]
  (let [[player-1 player-2] invite
        game-id (str (rand-int 10000))
        game-state (game/rand-game invite)
        turn (::game/turn (::game/round game-state))
        game-base {::game/id game-id
                   ::game/round-number 0
                   ::game/turn turn}]
    (alter games assoc game-id game-state)
    (bus/publish! player-bus player-1
                  {:menu/status :game-created
                   :menu/game (assoc game-base ::game/oppoent player-2)})
    (bus/publish! player-bus player-2
                  {:menu/status :game-created
                   :menu/game (assoc game-base ::game/opponent player-1)})))

(defmethod perform-action :add-invite
  [{invites :invites} [_ invite]]
  (alter invites conj invite))

(defmethod perform-action :remove-invite
  [{invites :invites} [_ invite]]
  (alter invites disj invite))

(defn consume-message
  [{:keys [games invites] :as deps} player raw-message]
  (try
    (log/debug "Consuming raw message:" raw-message)
    (let [parsed-message (edn/read-string raw-message)]
      (log/debug (str "Parsed message: " parsed-message))
      (dosync
       (let [actions (process-message {:games @games
                                       :invites @invites} player (edn/read-string raw-message))]
         (log/debug (str "Performing " (count actions) " actions."))
         (doseq [action actions]
           (println "ACT ACT:" action)
           (perform-action deps action)))))
    (catch Exception ex (log/error ex))))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn handle
  [{:keys [games invites game-bus player-bus] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [conn-id (uuid)
                   player (s/take! conn) ;; TODO: Timeout.
                   state (state-for @games @invites player)
                   state-message {:menu/status :state :menu/state state}]
        (log/debug (str "Initial menu state for " player ": " (with-out-str (clojure.pprint/pprint state))))
        ;; Give player current menu state
        (s/put! conn (pr-str state-message))
        ;; Game updates
        (doseq [game (:menu/games state)]
          (s/connect-via
           (bus/subscribe game-bus (::game/id game))
           (fn [message]
             (log/debug (str "Preparing game message for " player "... not really though.")))
           conn))
        ;; Player updates
        (s/connect-via
         (bus/subscribe player-bus player)
         (fn [message]
           (log/debug (str "Preparing player message for " player " [" conn-id "]"))
           (log/debug "Message:" message)
           (s/put! conn (pr-str message)))
         conn)
        ;; Consume messages from player
        (s/consume (partial consume-message deps player) conn)
        (log/debug (str "Player " player " ("
                        (count (:menu/games state)) " games, "
                        (count (:menu/sent-invites state)) " sent invites, "
                        (count (:menu/received-invites state)) " received invites) connected [" conn-id "]"))
        {:status 101}))))

(defn handler
  [deps]
  (partial handle deps))

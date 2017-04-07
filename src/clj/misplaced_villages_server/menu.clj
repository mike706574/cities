(ns misplaced-villages-server.menu
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
            [misplaced-villages-server.service :as service]
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

(defn invitations-for
  [invitations player]
  (filter #(some #{player} %) invitations))

(defmulti process-message
  (fn [invitations player message]
    (log/debug (str "Processing message: " message))
    (:menu/status message)))

(defmethod process-message :send-invitation
  [{:keys [player-bus invitations]} player {:keys [opponent]}]
  (log/debug (str "Processing request to send invitation from " player " to " opponent "."))
  (dosync
   (if (contains? @invitations [player opponent])
     ;; Invitation already exists
     (bus/publish! player-bus player {:menu/status :invitation-already-exists
                                      :menu/opponent opponent})
     ;; Create invitation
     (do (bus/publish! player-bus player {:menu/status :invitation-created
                                          :menu/opponent opponent})
         (bus/publish! player-bus opponent {:menu/status :invited
                                            :menu/opponent player})
         (alter invitations conj [player opponent])))))

(defmethod process-message :reject-invitation
  [{:keys [invitations player-bus]} player {:keys [opponent]}]
  (dosync
   ;; TODO: Handle other case
   (when (contains? @invitations [opponent player])
     (bus/publish! player-bus opponent {:menu/status :invitation-rejected
                                        :menu/opponent opponent})
     (alter invitations disj [opponent player]))))

(defmethod process-message :accept-invitation
  [{:keys [games invitations player-bus]} player {:keys [opponent] :as message}]
  (log/trace (str "Processing message: " message))
  (dosync
   (if-not (contains? @invitations [opponent player])
     ;; No invitation found
     (bus/publish! player-bus opponent {:menu/status :invitation-not-found
                                        :menu/opponent opponent})
     ;; Invitation found - create a game
     (let [game-id (rand-int 10000)
           game-state (game/rand-game [player opponent])]
       (bus/publish! player-bus player {:menu/status :game-created
                                        :menu/opponent opponent})
       (bus/publish! player-bus opponent {:menu/status :game-created
                                          :menu/opponent player})
       (alter games assoc game-id game-state)
       (alter invitations disj [opponent player])))))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn handle
  [{:keys [games invitations game-bus player-bus] :as deps} req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [conn-id (uuid)
                   player (s/take! conn) ;; TODO: Timeout.
                   games-for-player (games-for @games player)
                   invitations-for-player (invitations-for @invitations player)]
        ;; Give player current menu state
        (s/put! conn (pr-str {:menu/status :state
                              :menu/state {:menu/games games-for-player
                                           :menu/invitations invitations-for-player}}))
        ;; Game updates
        (doseq [game games-for-player]
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
        ;; Process actions
        (s/consume
         (fn [message]
           (process-message deps player (edn/read-string message)))
         conn)
        (log/debug (str "Player " player " (" (count games-for-player)
                        " games, " (count invitations-for-player)
                        " invitations) connected [" conn-id "]"))
        {:status 101}))))

(defn handler
  [deps]
  (partial handle deps))

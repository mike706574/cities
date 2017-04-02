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

(defn games-for-player
  [games player]
  (letfn [(playing? [game]
            (contains? (::game/players game) player))
          (transform [[id state]]
            (let [{:keys [::game/past-rounds
                          ::game/players
                          ::game/round]} state]
              {::game/id id
               ::game/round-number (count past-rounds)
               ::game/opponent (first (filter #(not= % player) players))
               ::game/turn (::game/turn round)}))]
    (into [] (comp (filter playing?)
                   (map transform))
          @games)))

(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn handle
  [games bus req]
  (d/let-flow [conn (d/catch
                        (http/websocket-connection req)
                        (fn [_] nil))]
    (if-not conn
      non-websocket-request
      (d/let-flow [player (s/take! conn)
                   games-for-player (games-for-player games player)]
        (println (str "Player " player " connected."))
        ;; Give player current menu state
        (s/put! conn (pr-str {:menu/status :connected
                              :menu/games games-for-player }))
        ;; Connect player to bus
        (doseq [game games-for-player]
          (s/connect-via
           (bus/subscribe bus (::game/id game))
           (fn [message]
             (println (str "Preparing menu message for " player "... not really though.")))
           conn))
        ;; Process menu actions
        {:status 101}))))

(defn handler
  [games bus]
  (partial handle games bus))

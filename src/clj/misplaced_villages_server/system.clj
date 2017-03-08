(ns misplaced-villages-server.system
  (:require [clojure.string :refer [blank?]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [ring.adapter.jetty :refer [run-jetty]]
            [liberator.core :refer [defresource]]
            [bidi.ring :refer (make-handler)]
            [misplaced-villages.game :as game]
            [misplaced-villages.card :as card]
            [misplaced-villages.player :as player]
            [misplaced-villages.move :as move]
            [misplaced-villages-server.service :refer [jetty-service]])
  (:gen-class :main true))

(def state (atom (game/start-game ["Mike" "Abby"])))

(defresource game
  :allow-methods [:get]
  :available-media-types ["application/json"]
  :handle-ok
  (fn [ctx]
    (if-let [player (get-in ctx [:request :headers "player"])]
      (let [state @state
            players (::game/players state)]
        (if-not (some #(= player %) players)
          (throw (ex-info (str player " is not playing..") {:players players}))
          (do (log/info (str "Returning data for " player "."))
              (game/for-player state player))))
      (do (log/info "No player specified - returning full game state.")
          @state))))

(def routes
  ["/" {"game" game}])

(defn system [config]
  {:app (jetty-service config (make-handler routes))})

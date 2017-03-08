(ns misplaced-villages-server.system
  (:require [clojure.string :refer [blank?]]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.cors :refer [wrap-cors]] ;
            [liberator.core :refer [defresource]]
            [bidi.ring :refer (make-handler)]
            [misplaced-villages.game :as game]
            [misplaced-villages.card :as card]
            [misplaced-villages.player :as player]
            [misplaced-villages.move :as move]
            [misplaced-villages-server.service :refer [jetty-service]])
  (:gen-class :main true))

(def game-state (atom (game/start-game ["Mike" "Abby"])))

(defn body-as-string
  [ctx]
  (if-let [body (get-in ctx [:request :body])]
    (condp instance? body
      java.lang.String body
      (slurp (io/reader body)))))

(defn parse-json
  [ctx key]
  (when (#{:put :post} (get-in ctx [:request :request-method]))
    (try
      (if-let [body (body-as-string ctx)]
        (let [data (json/read-str body :key-fn keyword)]
          [false {key data}])
        {:message "No body"})
      (catch Exception e
        (.printStackTrace e)
        {:message (format "IOException: %s" (.getMessage e))}))))'

(defn check-content-type
  [ctx content-types]
  (if (#{:put :post} (get-in ctx [:request :request-method]))
    (or
     (some #{(get-in ctx [:request :headers "content-type"])}
           content-types)
     [false {:message "Unsupported Content-Type"}])
    true))

(defresource game
  :allowed-methods [:get :put]
  :available-media-types ["application/json"]
  :malformed? (fn [ctx]
                (log/info "Malformed?")
                (parse-json ctx :body))
  :authorized? (fn [ctx]
                 (log/info "Authorized?")
                 (if (#{:put :post} (get-in ctx [:request :request-method]))
                   (let [players (::game/players @game-state)]
                     (if-let [player (get-in ctx [:request :headers "player"])]
                       (if (some #(= % player) players)
                         (do (log/info (str player " is playing."))
                             [true {:player player}])
                         [false {:message "You are not playing."}])
                       [false {:message "Who are you?"}]))
                   true))
  :handle-ok (fn [ctx]
               (if-let [player (get-in ctx [:request :headers "player"])]
                 (let [game-state @game-state
                       players (::game/players game-state)]
                   (if-not (some #(= player %) players)
                     (throw (ex-info (str player " is not playing..") {:players players}))
                     (do (log/info (str "Returning data for " player "."))
                         (game/for-player game-state player))))
                 (do (log/info "No player specified - returning full game state.")
                     @game-state)))
  :conflict? (fn [ctx]
               (log/info "Conflict?")
               (if (#{:put :post} (get-in ctx [:request :request-method]))
                 (let [player (:player ctx)
                       {:keys [card destination source]} (:body ctx)
                       card (-> card
                                (update :type keyword)
                                (update :color keyword)
                                (card/card))
                       destination (keyword destination)
                       source (keyword source)]
                   (log/info (str player ", " card ", " destination ", " source))
                   (let [move (move/move player card destination source)
                         {:keys [::game/status ::game/state] :as response} (game/take-action @game-state move)]
                     (log/info (str "Status: " status))
                     (if (= status :taken)
                       [false {:updated-game-state state}]
                       [true {:game-status status}])))
                 false))
  :put! (fn [ctx]
          (reset! game-state (:new-game-state ctx)))
  :handle-conflict (fn [ctx]

                     {:game-status (:game-status ctx)})
  :handle-exception (fn [ctx]
                      (let [exception (:exception ctx)]
                        (log/error (:exception ctx))
                        (ex-data exception))))

(def routes
  ["/" {"game" game}])


(defn system [config]
  {:app (jetty-service config (-> routes
                                  (make-handler)
                                  (wrap-cors :access-control-allow-origin [#".*"]
                                             :access-control-allow-methods [:get :put])))})

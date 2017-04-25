(ns milo.server.websocket
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [manifold.bus :as bus]
            [milo.server.connection :as conn]
            [milo.server.event :as event]
            [milo.server.message :refer [encode decode]]
            [milo.server.model :as model]
            [taoensso.timbre :as log]))

(defn non-websocket-response
  []
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn handle
  [{:keys [games invites player-bus conn-manager] :as deps} req]
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

(defn handler
  [deps]
  (partial handle deps))

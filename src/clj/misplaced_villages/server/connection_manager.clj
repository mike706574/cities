(ns misplaced-villages.server.connection-manager
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [manifold.stream :as s])
  (:gen-class :main true))

(defrecord ConnectionManager [conns]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

(defn connection-manager
  [connections]
  (ConnectionManager. connections))

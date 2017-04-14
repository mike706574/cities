(ns milo.server.storage
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as s]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(defprotocol Storage
  "Stores things."
  (swap-game! [this id f args]
    "Atomically swaps the value of game id to be (apply f current-state
    args)."))

(defrecord RefStorage [games]
  Storage
  (swap-game! [this id f args]
    (dosync
     (let [state (get @games id)]
       (alter games (fn [games] (update games id #(apply f % args))))))))

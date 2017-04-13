(ns misplaced-villages.server.storage
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as s]
            [misplaced-villages.server.util :as util]
            [taoensso.timbre :as log]))

(defprotocol Storage
  "Stores things."
  (swap-game! [this id f]
    "Atomically swaps the value of game id to be (apply f current-state
    args)."))

(defrecord RefStorage [games]
  Storage
  (swap-game! [this id f]
    (dosync
     (let [state (get @games id)]
       (alter games (fn [games] (update games id #(apply f % args))))))))

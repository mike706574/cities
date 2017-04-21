(ns milo.server.event
  (:require [com.stuartsierra.component :as component]
            [manifold.stream :as s]
            [milo.server.util :as util]
            [taoensso.timbre :as log]))

(defprotocol EventManager
  "Manages events."
  (store [this data] "Get the next event identifier."))

(defrecord RefEventManager [counter events]
  EventManager
  (store [this data]
    (let [id (alter counter inc)
          event (assoc data :milo/event-id id)]
      (alter events assoc id event)
      event)))

(defn manager
  []
  (component/using (map->RefEventManager {:counter (ref 0)})
    [:events]))

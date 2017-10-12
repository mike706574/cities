(ns cities.client.macros)

(defmacro guard-event
  [db event & body]
  `(if (contains? (:events ~db) (:cities/event-id ~event))
     ~db
     (let [~'db (update ~db :events assoc (:cities/event-id ~event) ~event)]
       ~@body)))

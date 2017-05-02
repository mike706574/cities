(ns milo.client.macros)

(defmacro guard-event
  [db event & body]
  `(do
     (println "event guard activate")
     (if (contains? (:events ~db) (:milo/event-id ~event))
       ~db
       (let [~'db (update ~db :events assoc (:milo/event-id ~event) ~event)]
         ~@body))))

(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [cities.backend.system :as system]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(repl/set-refresh-dirs "src")

(log/set-level! :trace)

(def config {:id "cities-server" :port 8001})

(defonce system nil)

(defn init []
  (alter-var-root #'system (constantly (system/system config)))
  :initialized)

(defn start []
  (try
    (if (nil? system)
      :uninitialized
      (do
        (alter-var-root #'system component/start-system)
        :started))
    (catch Exception ex
      (log/error (or (.getCause ex) ex) "Failed to start system.")
      :failed)))

(defn stop []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop-system s))))
  :stopped)

(defn go []
  (init)
  (start)
  :ready)

(defn reset []
  (stop)
  (repl/refresh :after `go))

(defn restart []
  (stop)
  (go))

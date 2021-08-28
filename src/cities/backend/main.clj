(ns cities.backend.main
  (:require [cities.backend.system :as system]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]))

(defn -main [& [port]]
  (log/set-level! :debug)
  (let [port (Integer. (or port (env :port) 5000))]
    (let [config {:id "cities" :port port}
          system (system/system config)]
      (log/info "Starting system." config)
      (component/start-system system)
      (log/info "Waiting forever.")
      @(promise))))

(ns milo.server.main
  (:require [com.stuartsierra.component :as component]
            [milo.server.system :as system]
            [taoensso.timbre :as log])
  (:gen-class :main true))

(def port 8080)

(defn -main
  [_]
  (log/info "Starting up on port " port "!")
  (component/start-system
   (system/system {:id "milo" :port port}))
  @(promise))

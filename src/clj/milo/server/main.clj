(ns milo.server.main
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [milo.server.system :as system]
            [taoensso.timbre :as log])
  (:gen-class :main true))

(defn -main
  [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (log/info (str "Using port " port "."))
    (component/start-system
     (system/system {:id "milo" :port port}))
    @(promise)))

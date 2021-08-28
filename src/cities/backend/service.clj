(ns cities.backend.service
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [aleph.http :as aleph-http]
            [cities.backend.connection :as conn]
            [cities.backend.handler :as handler])
  (:gen-class :main true))

(defn info [service]
  (select-keys service [:port]))

(defn- already-started
  [service]
  (log/info "Service already started.")
  service)

(defn- start-service
  [{:keys [port] :as service} handler-factory]
  (let [info (info service)]
    (try
      (log/info "Starting service." info)
      (let [handler (handler/handler handler-factory)
            server (aleph-http/start-server handler {:port port})]
        (log/info "Finished starting service." info)
        (assoc service :server server))
      (catch java.net.BindException e
        (throw (ex-info "Service port already in use." info))))))

(defn- stop-service
  [{:keys [port server conn-manager] :as service}]
  (log/info "Stopping service." {:port port})
  (conn/close-all! conn-manager)
  (.close server)
  (dissoc service :server))

(defn- already-stopped
  [service]
  (log/info "Service already stopped.")
  service)

(defrecord AlephService [port handler-factory server conn-manager]
  component/Lifecycle
  (start [this]
    (if server
      (already-started this)
      (start-service this handler-factory)))
  (stop [this]
    (if server
      (stop-service this)
      (already-stopped this))))

(defn aleph-service
  [{:keys [port] :as config}]
  {:pre [(integer? port)
         (> port 0)]}
  (component/using
   (map->AlephService config)
   [:handler-factory :conn-manager]))

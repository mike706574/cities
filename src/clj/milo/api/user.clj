(ns milo.api.user
  (:require [com.stuartsierra.component :as component]))

(defprotocol UserManager
  "Manages users."
  (credentials [this username])
  (avatar [this username]))

(defrecord AtomUserManager [users]
  UserManager
  (credentials [this username]
    (get @users username))
  (avatar [this username]
    (:avatar (get @users username))))

(defn manager
  []
  (component/using (map->AtomUserManager {})
    [:users]))

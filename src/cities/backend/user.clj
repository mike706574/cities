(ns cities.backend.user
  (:require [com.stuartsierra.component :as component]))

(defprotocol UserManager
  "Manages users."
  (user [this username])
  (users [this])
  (add! [this user]))

(defrecord AtomUserManager [users]
  UserManager
  (user [this username]
    (get @users username))
  (users [this]
    (vec (vals @users)))
  (add! [this user]
    (swap! users assoc (:name user) user)))

(defn manager []
  (component/using (map->AtomUserManager {})
    [:users]))

(ns cities.server.site.handler
  (:require [cemerick.friend :as friend]
            [cemerick.friend.credentials :as creds]
            [cemerick.friend.workflows :as workflows]
            [cities.api.user :as user-api]
            [cities.server.site.routes :as site-routes]
            [ring.middleware.defaults :refer [wrap-defaults
                                              site-defaults]]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn handler
  [{:keys [user-manager] :as deps}]
  (-> (site-routes/routes deps)
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn
                                (partial user-api/credentials user-manager))
        :workflows [(workflows/interactive-form)]})
      (wrap-resource "public")
      (wrap-defaults site-defaults)))

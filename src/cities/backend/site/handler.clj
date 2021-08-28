(ns cities.backend.site.handler
  (:require [cities.backend.user :as user-api]
            [cities.backend.site.routes :as site-routes]
            [ring.middleware.defaults :refer [wrap-defaults
                                              site-defaults]]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn handler
  [{:keys [user-manager] :as deps}]
  (-> (site-routes/routes deps)
      (wrap-resource "public")
      (wrap-defaults site-defaults)))

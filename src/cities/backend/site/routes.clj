(ns cities.backend.site.routes
    (:require [clojure.string :as str]
              [clojure.spec.alpha :as spec]
              [compojure.core :as compojure :refer [ANY DELETE GET POST PUT]]
              [compojure.route :as route]
              [com.stuartsierra.component :as component]
              [cities.backend.user :as user-api]
              [cities.card :as card]
              [ring.middleware.defaults :refer [wrap-defaults
                                                site-defaults
                                                api-defaults]]
              [ring.middleware.resource :refer [wrap-resource]]
              [ring.util.response :as response]
              [selmer.parser :as selmer]
              [taoensso.timbre :as log]))

(defn routes
  [{:keys [games game-bus] :as deps}]
  (compojure/routes
   (GET "/" req
        (selmer/render-file "templates/index.html" {}))

   (GET "/:player" []
        (selmer/render-file "templates/game.html" {}))

   (route/not-found "No such page.")))

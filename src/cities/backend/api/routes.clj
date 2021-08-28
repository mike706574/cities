(ns cities.backend.api.routes
  (:require [compojure.core :as compojure :refer [ANY DELETE GET POST PUT]]
            [compojure.route :as route]
            [cities.backend.api.resource :as resource]
            [cities.backend.api.websocket :as websocket]))

(defn routes
  [deps]
  (compojure/routes
   (GET "/api/game/:id" request (resource/handle-game-retrieval deps request))
   (PUT "/api/game/:id" request (resource/handle-turn deps request))
   (POST "/api/game" request (resource/handle-accepting-invite deps request))
   (GET "/api/menu" request (resource/handle-menu-retrieval deps request))
   (POST "/api/invite" request (resource/handle-sending-invite deps request))
   (GET "/api/invites" request (resource/handle-sending-invite deps request))
   (DELETE "/api/invite/:sender/:recipient" request (resource/handle-deleting-invite deps request))
   (GET "/api/websocket" [] (websocket/handler deps))
   (route/not-found {:status 200})))

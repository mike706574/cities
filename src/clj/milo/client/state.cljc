(ns milo.client.state
  (:require [ajax.core :as ajax]))

(defn play-game
  [{db :db} [_ game-id]]
  (if-let [game (get-in db [:milo/active-games game-id])]
    (if (:loaded? game)
      (game-screen game)
      {:db (-> db
               (dissoc :move-message)
               (assoc :loading? true))
       :http-xhrio {:method :get
                    :uri (str "/api/game/" game-id)
                    :headers {"Player" player}
                    :format (ajax/transit-request-format)
                    :response-format (ajax/transit-response-format)
                    :on-success [:retrieved-game]
                    :on-failure [:failed-to-retrieve-game]}}
      nil ;; TODO
      )
    ))

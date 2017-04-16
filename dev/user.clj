(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [aleph.http :as http]
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [cognitect.transit :as transit]
   [com.stuartsierra.component :as component]
   [manifold.stream :as s]
   [manifold.deferred :as d]
   [manifold.bus :as bus]
   [milo.card :as card]
   [milo.game :as game]
   [milo.move :as move]
   [milo.player :as player]
   [milo.server.system :as system]
   [milo.server.menu :as menu]
   [milo.server.message :as message]))

(def config {:id "milo-server" :port 8001})

(defonce system nil)

(defn init
  "Creates and initializes the system under development in the Var
  #'system."
  []
  (alter-var-root #'system (constantly (system/system config)))
  :init)

(defn start
  "Starts the system running, updates the Var #'system."
  []
  (alter-var-root #'system component/start-system)
  :started)

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop-system s))))
  :stopped)

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after `go))

(defn restart
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (go))

(defn check
  [conn]
  (let [message @(s/try-take! conn :drained 1000 :timeout)]
    (println
     (case message
       :drained "DRAINED! Something probably blew up!"
       :timeout "TIMEOUT! Timed out waiting for message...!"
       (do ;;(println "I HAVE TO PARSE:" message)
         (str "*** START MESSAGE ***\n"
              (with-out-str (clojure.pprint/pprint (edn/read-string message)))
              "**** END MESSAGE ****\n"))))))

(defn invite
  []
  (let [conn @(http/websocket-client "ws://localhost:8001/menu-websocket")]
    (s/put! conn "Abby")
    (check conn)
    (s/put! conn "{:menu/status :send-invite :opponent \"Mike\"}")
    (check conn)))

(defn inviting
  []
  (let [mike @(http/websocket-client "ws://localhost:8001/menu-websocket")
        abby @(http/websocket-client "ws://localhost:8001/menu-websocket")]
    (s/put! mike "Mike")
    (s/put! abby "Abby")
    (check abby)
    (s/put! abby "{:menu/status :send-invite :opponent \"Mike\"}")
    (check abby)
    (s/close! mike)
    (s/close! abby)))

(comment
  @(http/get "http://localhost:8001/")

  (-> system
      :games
      deref

)

 (-> @(http/put "http://localhost:8001/api/game/1"
                {:headers {"Content-Type" "application/transit+json"
                           "Player" "abby"
                           "Accept" "application/transit+json"}
                 :body (message/encode (move/exp* "abby" (card/number :blue 5)))
                 :throw-exceptions false})
     :body
     slurp
     message/decode
)
   @(http/websocket-client "ws://localhost:8001/menu-websocket")
  )

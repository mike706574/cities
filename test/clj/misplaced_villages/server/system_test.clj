(ns misplaced-villages.server.system-test
  (:require [aleph.http :as http]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest testing is]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [misplaced-villages.game :as game]
            [misplaced-villages.server.system :as system]
            [misplaced-villages.server.message :refer [encode decode]]
            [taoensso.timbre :as log]))

(def config {:id "test" :port 10000})

(defmacro with-system
  [& body]
  `(let [~'system (component/start-system (system/system config))]
     (try
       ~@body
       (finally (component/stop-system ~'system)))))

(defn receive!
  [conn]
  (let [out @(s/try-take! conn :drained 1000 :timeout)]
    (if (contains? #{:drained :timeout} out) out (decode out))))

(defn send!
  [conn message]
  (s/put! conn (encode message)))

(comment
  (try
    (with-system
      (let [mike-conn @(http/websocket-client "ws://localhost:10000/menu-websocket")]
        (send! mike-conn "mike")
        (receive! mike-conn)))
    (catch Exception ex
      (log/debug (.getCause ex))))

  )

(deftest inviting
  (with-system
    (let [mike-conn @(http/websocket-client "ws://localhost:10000/menu-websocket")
          abby-conn @(http/websocket-client "ws://localhost:10000/menu-websocket")]

      (send! mike-conn "mike")
      (is (= {:menu/status :state
              :menu/state {:menu/games {},
                           :menu/received-invites #{},
                           :menu/sent-invites #{}}}
             (receive! mike-conn)))

      (send! abby-conn "abby")
      (is (= {:menu/status :state
              :menu/state {:menu/games {},
                           :menu/received-invites #{},
                           :menu/sent-invites #{}}}
             (receive! abby-conn)))

      (send! mike-conn {:menu/status :send-invite :menu/player "abby"})
      (is (= {:menu/status :sent-invite :menu/invite ["mike" "abby"]}
             (receive! mike-conn)))

      (is (= {:menu/status :received-invite :menu/invite ["mike" "abby"]}
             (receive! abby-conn)))

      (send! abby-conn {:menu/status :accept-invite :menu/player "mike"})

      (let [{:keys [:menu/status :menu/game]} (receive! abby-conn)
            {:keys [::game/id
                    ::game/round-number
                    ::game/turn
                    ::game/opponent]} game]
        (is (= status :game-created))
        (is (= "1" id))
        (is (= 1 round-number))
        (is (contains? #{"mike" "abby"} turn))
        (is "mike" opponent))

      (let [{:keys [:menu/status :menu/game]} (receive! mike-conn)
            {:keys [::game/id
                    ::game/round-number
                    ::game/turn
                    ::game/opponent]} game]
        (is (= status :game-created))
        (is (= "1" id))
        (is (= 1 round-number))
        (is (contains? #{"mike" "abby"} turn))
        (is "abby" opponent)))))

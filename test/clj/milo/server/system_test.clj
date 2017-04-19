(ns milo.server.system-test
  (:require [aleph.http :as http]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest testing is]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [milo.card :as card]
            [milo.game :as game]
            [milo.menu :as menu]
            [milo.server.system :as system]
            [milo.server.message :refer [encode decode]]
            [taoensso.timbre :as log]))


(def first-8
  [(card/number :yellow 7)
   (card/number :yellow 6)
   (card/number :yellow 10)
   (card/number :yellow 8)
   (card/wager :yellow 3)
   (card/number :green 3)
   (card/wager :blue 3)
   (card/number :green 10)])

(def second-8
  [(card/number :blue 3)
   (card/number :yellow 2)
   (card/number :blue 9)
   (card/number :white 9)
   (card/number :white 5)
   (card/number :yellow 9)
   (card/wager :white 2)
   (card/wager :white 1)])

(def last-4
  [(card/number :yellow 4)
   (card/number :blue 8)
   (card/wager :red 2)
   (card/wager :red 3)])

(def deck-1 (concat first-8 second-8 last-4))

(def test-game (game/game ["mike" "abby"] [deck-1 deck-1 deck-1] 4))

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
      (is (= {::menu/status :state
              ::menu/active-games {}
              ::menu/completed-games {}
              ::menu/received-invites #{},
              ::menu/sent-invites #{}}
             (receive! mike-conn)))

      (send! abby-conn "abby")
      (is (= {::menu/status :state
              ::menu/active-games {},
              ::menu/completed-games {},
              ::menu/received-invites #{},
              ::menu/sent-invites #{}}
             (receive! abby-conn)))

      (send! mike-conn {::menu/status :send-invite ::menu/player "abby"})
      (is (= {::menu/status :sent-invite ::menu/invite ["mike" "abby"]}
             (receive! mike-conn)))

      (is (= {::menu/status :received-invite ::menu/invite ["mike" "abby"]}
             (receive! abby-conn)))

      (send! abby-conn {::menu/status :accept-invite ::menu/player "mike"})

      (let [{:keys [::menu/status ::menu/game]} (receive! abby-conn)
            {:keys [::game/id
                    ::game/round-number
                    ::game/turn
                    ::game/opponent]} game]
        (is (= status :game-created))
        (is (= "1" id))
        (is (= 1 round-number))
        (is (contains? #{"mike" "abby"} turn))
        (is "mike" opponent))

      (let [{:keys [::menu/status ::menu/game]} (receive! mike-conn)
            {:keys [::game/id
                    ::game/round-number
                    ::game/turn
                    ::game/opponent]} game]
        (is (= status :game-created))
        (is (= "1" id))
        (is (= 1 round-number))
        (is (contains? #{"mike" "abby"} turn))
        (is "abby" opponent)))))

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

(defn flush!
  [conn]
  (loop [out :continue]
    (when (not= out :timeout)
      (recur @(s/try-take! conn :drained 10 :timeout)))))

(defn send!
  [conn message]
  (s/put! conn (encode message)))

(defn parse
  [request]
  (if (contains? request :body)
    (update request :body (comp decode slurp))
    request))

;; TODO: Handle errors
(defn connect!
  [id]
  (let [conn @(http/websocket-client "ws://localhost:10000/menu-websocket")]
    (send! conn id)
    (flush! conn)
    conn))

(deftest cancelling-an-invite
  (with-system
    (let [mike-conn (connect! "mike")
          abby-conn (connect! "abby")]
      (dosync (alter (:invites system) conj ["mike" "abby"]))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (let [{:keys [status body]} (parse @(http/delete "http://localhost:10000/api/invite/mike/abby"
                                                     {:headers {"Player" "mike"
                                                                "Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}
                                                      :throw-exceptions false}) )]
        (is (= 200 status))
        (is (= {:milo/event-id 1
                :milo/status :sent-invite-canceled
                :milo.menu/invite ["mike" "abby"]} body)))
      (is (= #{} @(:invites system)))
      (is (= {:milo/event-id 1
              :milo/status :sent-invite-canceled
              :milo.menu/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= {:milo/event-id 1
              :milo/status :received-invite-cancelled
              :milo.menu/invite ["mike" "abby"]} (receive! abby-conn))))))

(deftest rejecting-an-invite
  (with-system
    (let [mike-conn (connect! "mike")
          abby-conn (connect! "abby")]
      (dosync (alter (:invites system) conj ["mike" "abby"]))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (let [{:keys [status body]} (parse @(http/delete "http://localhost:10000/api/invite/mike/abby"
                                                     {:headers {"Player" "abby"
                                                                "Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}
                                                      :throw-exceptions false}) )]
        (is (= 200 status))
        (is (= {:milo/event-id 1
                :milo/status :received-invite-rejected
                :milo.menu/invite ["mike" "abby"]} body)))
      (is (= #{} @(:invites system)))
      (is (= {:milo/event-id 1
              :milo/status :sent-invite-rejected
              :milo.menu/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= {:milo/event-id 1
              :milo/status :received-invite-rejected
              :milo.menu/invite ["mike" "abby"]} (receive! abby-conn))))))

(deftest sending-an-invite
  (with-system
    (let [mike-conn (connect! "mike")
          abby-conn (connect! "abby")]
      (is (= #{} @(:invites system)))
      (let [{:keys [status body]} (parse @(http/post "http://localhost:10000/api/invite"
                                                     {:headers {"Player" "mike"
                                                                "Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}
                                                      :body (encode ["mike" "abby"])
                                                      :throw-exceptions false}) )]
        (is (= 201 status))
        (is (= {:milo/event-id 1
                :milo/status :sent-invite
                :milo.menu/invite ["mike" "abby"]} body)))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (is (= {:milo/event-id 1
              :milo/status :sent-invite
              :milo.menu/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= {:milo/event-id 1
              :milo/status :received-invite
              :milo.menu/invite ["mike" "abby"]} (receive! abby-conn))))))

(deftest accepting-an-invite
  (with-system
    (let [mike-conn (connect! "mike")
          abby-conn (connect! "abby")]
      (dosync (alter (:invites system) conj ["mike" "abby"]))
      (let [{:keys [status body]} (parse @(http/post "http://localhost:10000/api/game"
                                                     {:headers {"Player" "abby"
                                                                "Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}
                                                      :body (encode ["mike" "abby"])
                                                      :throw-exceptions false}) )]
        (is (= 201 status))
        (is (= {:milo/event-id 1
                :milo/status :sent-invite
                :milo.menu/invite ["mike" "abby"]}
               (dissoc body :milo.game/game))))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (is (= {:milo/event-id 1
              :milo/status :sent-invite
              :milo.menu/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= {:milo/event-id 1
              :milo/status :received-invite
              :milo.menu/invite ["mike" "abby"]} (receive! abby-conn))))))

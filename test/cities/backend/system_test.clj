(ns cities.backend.system-test
  (:require [aleph.http :as http]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [deftest testing is]]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [cities.card :as card]
            [cities.game :as game]
            [cities.backend.system :as system]
            [cities.backend.message :refer [encode decode]]
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
  (let [port (:port config)
        ws-url (str "ws://localhost:" port "/api/websocket")]
    `(let [~'system (component/start-system (system/system config))
           ~'ws-url ~ws-url
           ~'http-url #(str "http://localhost:" ~port %)]
       (try
         ~@body
         (finally (component/stop-system ~'system))))))

(defn receive!
  [conn]
  (let [out @(s/try-take! conn :drained 2000 :timeout)]
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
  [ws-url id]
  (println "CONNECTIN 2" ws-url)
  (let [conn @(http/websocket-client ws-url)]
    (send! conn id)
    (is (not= :timeout
              (receive! conn)))
    conn))

(deftest canceling-an-invite
  (with-system
    (let [mike-conn (connect! ws-url "mike")
          abby-conn (connect! ws-url "abby")]
      (dosync (alter (:invites system) conj ["mike" "abby"]))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (let [{:keys [status body]} (parse @(http/delete (http-url "/api/invite/mike/abby")
                                                       {:headers {"Player" "mike"
                                                                  "Content-Type" "application/transit+json"
                                                                  "Accept" "application/transit+json"}
                                                        :throw-exceptions false}))]
        (is (= 200 status))
        (is (= {:cities/event-id 1
                :cities/status :sent-invite-canceled
                :cities/invite ["mike" "abby"]} body)))
      (is (= #{} @(:invites system)))
      (is (= {:cities/event-id 1
              :cities/status :sent-invite-canceled
              :cities/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= #:cities{:status :received-invite-canceled,
                    :invite ["mike" "abby"],
                    :event-id 1}
             (receive! abby-conn))))))

(deftest rejecting-an-invite
  (with-system
    (let [mike-conn (connect! ws-url "mike")
          abby-conn (connect! ws-url "abby")]
      (dosync (alter (:invites system) conj ["mike" "abby"]))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (let [{:keys [status body]} (parse @(http/delete (http-url "/api/invite/mike/abby")
                                                       {:headers {"Player" "abby"
                                                                  "Content-Type" "application/transit+json"
                                                                  "Accept" "application/transit+json"}
                                                        :throw-exceptions false}) )]
        (is (= 200 status))
        (is (= {:cities/event-id 1
                :cities/status :received-invite-rejected
                :cities/invite ["mike" "abby"]} body)))
      (is (= #{} @(:invites system)))
      (is (= {:cities/event-id 1
              :cities/status :sent-invite-rejected
              :cities/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= {:cities/event-id 1
              :cities/status :received-invite-rejected
              :cities/invite ["mike" "abby"]} (receive! abby-conn))))))

(deftest sending-an-invite
  (with-system
    (let [mike-conn (connect! ws-url "mike")
          abby-conn (connect! ws-url "abby")]
      (is (= #{} @(:invites system)))
      (let [{:keys [status body]} (parse @(http/post (http-url "/api/invite")
                                                     {:headers {"Player" "mike"
                                                                "Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}
                                                      :body (encode ["mike" "abby"])
                                                      :throw-exceptions false}) )]
        (is (= 201 status))
        (is (= {:cities/event-id 1
                :cities/status :sent-invite
                :cities/invite ["mike" "abby"]} body)))
      (is (= #{["mike" "abby"]} @(:invites system)))
      (is (= {:cities/event-id 1
              :cities/status :sent-invite
              :cities/invite ["mike" "abby"]} (receive! mike-conn)))
      (is (= {:cities/event-id 1
              :cities/status :received-invite
              :cities/invite ["mike" "abby"]} (receive! abby-conn))))))

(deftest accepting-an-invite
  (with-system
    (let [mike-conn (connect! ws-url "mike")
          abby-conn (connect! ws-url "abby")]
      (dosync (alter (:invites system) conj ["mike" "abby"]))
      (let [{:keys [status body]} (parse @(http/post (http-url "/api/game")
                                                     {:headers {"Player" "abby"
                                                                "Content-Type" "application/transit+json"
                                                                "Accept" "application/transit+json"}
                                                      :body (encode ["mike" "abby"])
                                                      :throw-exceptions false}))]
        (is (= 201 status))
        (is (= #:cities{:status :game-created, :invite ["mike" "abby"], :event-id 1}
               (dissoc body :cities.game/game))))
      (is (= #{} @(:invites system)))
      (let [turn (get-in (get @(:active-games system) "1")
                         [:cities.game/round :cities.game/turn])]
        (is (= {:cities/status :game-created
                :cities.game/game
                #:cities.game{:id "1"
                            :opponent "abby"
                            :over? false
                            :loaded? false
                            :round #:cities.game{:turn turn}
                            :round-number 1}
                :cities/invite ["mike" "abby"]
                :cities/event-id 1}
               (receive! mike-conn)))
        (is (= {:cities/status :game-created
                :cities.game/game
                #:cities.game{:id "1"
                            :opponent "mike"
                            :over? false
                            :loaded? false
                            :round #:cities.game{:turn turn},
                            :round-number 1}
                :cities/invite ["mike" "abby"]
                :cities/event-id 1}
               (receive! abby-conn)))))))

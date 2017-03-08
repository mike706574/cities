(ns misplaced-villages-server.system-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [clj-http.client :as http]
            [misplaced-villages-server.system :as system]
            [clojure.data.json :as json]))

(def config {:id "misplaced-villages-server" :port 8081})

(defmacro with-system
  [& body]
  `(let [~'system (component/start-system (system/system config))]
     (try
       ~@body
       (finally (component/stop-system ~'system)))))

(deftest saying-hello
  (with-system
    (testing "should say hello in English by default"
      (let [{:keys [status headers body]} (http/get "http://localhost:8081/hello")]
        (println headers)
        (is (= 200 status))
        (is (= "text/plain;charset=UTF-8" (get headers "Content-Type")))
        (is (= "Hello, world!" body))))))

(defproject org.clojars.mike706574/misplaced-villages-server "0.0.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [com.taoensso/timbre "4.8.0"]
                 [manifold "0.1.6"]
                 [byte-streams "0.2.2"]
                 [com.stuartsierra/component "0.3.2"]
                 [environ "1.1.0"]
                 [aleph "0.4.3"]
                 [ring-cors "0.1.9"]
                 [compojure "1.5.2"]
                 [org.clojars.mike706574/misplaced-villages "0.0.1-SNAPSHOT"]]
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :profiles {:uberjar {:aot :all
                       :main misplaced-villages-server.main}
             :dev {:source-paths ["dev"]
                   :target-path "target/dev"
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/test.check "0.9.0"]
                                  [clj-http "3.4.1"]
                                  [org.clojure/data.json "0.2.6"]]}})

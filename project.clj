(defproject cities "0.0.1-SNAPSHOT"
  :description "Lost cities."
  :url "https://mike-cities.herokuapp.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/core.async "1.3.618"]
                 [org.clojure/test.check "1.1.0"]
                 [aleph "0.4.7-alpha7"]
                 [byte-streams "0.2.4"]
                 [com.cognitect/transit-clj "1.0.324"]
                 [com.stuartsierra/component "1.0.0"]
                 [com.taoensso/timbre "5.1.2"]
                 [compojure "1.6.2"]
                 [environ "1.2.0"]
                 [manifold "0.1.9-alpha5"]
                 [ring/ring-anti-forgery "1.3.0"]
                 [ring/ring-defaults "0.3.3"]
                 [selmer "1.12.44"]]
  :min-lein-version "2.0.0"
  :plugins [[lein-shell "0.5.0"]]
  :uberjar-name "cities.jar"
  :profiles {:uberjar {:env {:production true}
                       :prep-tasks ["frontend"]
                       :aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "1.1.0"]]}}
  :aliases {"frontend" ["shell" "npx" "shadow-cljs" "release" "frontend"]})

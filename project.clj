(defproject org.clojars.mike706574/milo-webapp "0.0.1-SNAPSHOT"
  :description "A card game."
  :url "https://github.com/mike706574/milo-webapp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.3.442"]
                 [com.stuartsierra/component "0.3.2"]

                 ;; Game
                 [org.clojars.mike706574/milo "0.0.1-SNAPSHOT"]

                 ;; Utility
                 [com.cognitect/transit-clj "0.8.300"]
                 [manifold "0.1.6"]
                 [byte-streams "0.2.2"]
                 [environ "1.1.0"]

                 ;; Logging
                 [com.taoensso/timbre "4.8.0"]

                 ;; Web
                 [aleph "0.4.3"]
                 [ring/ring-anti-forgery "1.0.1"]
                 [ring/ring-defaults "0.2.3"]
                 [ring-middleware-format "0.7.2"]
                 [compojure "1.5.2"]
                 [com.cemerick/friend "0.3.0-SNAPSHOT"]
                 [selmer "1.10.7"]

                 ;; ClojureScript
                 [org.clojure/clojurescript "1.9.521"]
                 [com.cognitect/transit-cljs "0.8.239"]

                 [cljsjs/react-with-addons "15.4.2-2"]
                 [com.yetanalytics/re-mdl "0.1.5"]
                 [reagent "0.6.1" :exclusions [cljsjs/react]]
                 [re-frame "0.9.2" :exclusions [cljsjs/react]]

                 [day8.re-frame/http-fx "0.1.3"]
                 [cljs-ajax "0.5.9"]]
  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj"]
  :plugins [[com.palletops/uberimage "0.4.1"]
            [lein-cljsbuild "1.1.5"]
            [cider/cider-nrepl "0.14.0"]
            [org.clojure/tools.nrepl "0.2.12"]
            [lein-figwheel "0.5.9"]]
  :profiles {:dev {:source-paths ["dev"]
                   :target-path "target/dev"
                   :dependencies [[org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.9"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :production {:aot :all
                          :main milo.server.main
                          :uberjar-name "milo-webapp.jar"}}
  :cljsbuild {:builds {:dev {:source-paths ["src/cljs"]
                             :compiler {:main "milo.client.core"
                                        :asset-path "js"
                                        :closure-defines {milo.client.events/server "goose:8001"}
                                        :source-map true
                                        :source-map-timestamp true
                                        :optimizations :none
                                        :output-dir "resources/public/js"
                                        :output-to "resources/public/client.js"}}
                       :production {:source-paths ["src/cljs"]
                                    :compiler {:output-dir "target/js"
                                               :optimizations :advanced
                                               :elide-asserts true
                                               :closure-defines {milo.client.events/server "misplaced-villages.herokuapp.com"}
                                               :pretty-print false
                                               :output-to "resources/public/client.js"}}}}
  :figwheel {:repl false
             :http-server-root "public"}
  :clean-targets ^{:protect false} ["resources/public/client.js" "resources/public/js"]
  :aliases {"production-build" ["with-profile" "production" "do" "clean" ["cljsbuild" "once" "production"] ["uberjar"]]})

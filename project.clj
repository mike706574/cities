(defproject org.clojars.mike706574/misplaced-villages-server "0.0.1-SNAPSHOT"
  :description "Describe me!"
  :url "https://github.com/mike706574/misplaced-villages-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha15"]
                 [com.stuartsierra/component "0.3.2"]
                 [org.clojure/core.async "0.3.442"]

                 ;; Utility
                 [manifold "0.1.6"]
                 [byte-streams "0.2.2"]

                 ;; Logging
                 [com.taoensso/timbre "4.8.0"]

                 ;; Web
                 [aleph "0.4.3"]
                 [ring-cors "0.1.9"]
                 [compojure "1.5.2"]

                 ;; ClojureScript
                 [org.clojure/clojurescript "1.9.495"]
                 [reagent "0.6.1"]
                 [reagent-utils "0.2.1"]
                 [re-frame "0.9.2"]
                 [day8.re-frame/http-fx "0.1.3"]
                 [cljs-ajax "0.5.8"]
                 [org.clojars.mike706574/misplaced-villages "0.0.1-SNAPSHOT"]]

  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :plugins [[lein-cljsbuild "1.1.5"]
            [cider/cider-nrepl "0.14.0"]
            [org.clojure/tools.nrepl "0.2.12"]
            [lein-figwheel "0.5.9"]]
  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:source-paths ["dev"]
                   :target-path "target/dev"
                   :dependencies [
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [clj-http "3.4.1"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.9"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :cljsbuild
                   {:builds
                    {:game
                     {:figwheel {:on-jsload "misplaced-villages-client.game.core/run"
                                 :websocket-host "192.168.1.141"
                                 }
                      :compiler {:main "misplaced-villages-client.game.core"
                                 :asset-path "game"
                                 :optimizations :none
                                 :source-map true
                                 :source-map-timestamp true}}
                     :menu
                     {:figwheel {:on-jsload "misplaced-villages-client.menu.core/run"
                                 :websocket-host "192.168.1.141"
                                 }
                      :compiler {:main "misplaced-villages-client.menu.core"
                                 :asset-path "menu"
                                 :optimizations :none
                                 :source-map true
                                 :source-map-timestamp true}}}}}}
  :cljsbuild {:builds {:game {:source-paths ["src/cljs"]
                              :compiler {:output-dir "resources/public/game"
                                         :output-to "resources/public/game/core.js"}}
                       :menu {:source-paths ["src/cljs"]
                              :compiler {:output-dir "resources/public/menu/"
                                         :output-to "resources/public/menu/core.js"}}}}
  :figwheel {:repl false
             :http-server-root "public"}
  :clean-targets ^{:protect false} ["resources/public/game"
                                    "resources/public/menu"])

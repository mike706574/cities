(defproject org.clojars.mike706574/milo-webapp "0.0.1-SNAPSHOT"
  :description "Describe me!"
  :url "https://github.com/mike706574/milo-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha16"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.3.442"]
                 [com.stuartsierra/component "0.3.2"]

                 ;; Game
                 [org.clojars.mike706574/milo "0.0.1-SNAPSHOT"]

                 ;; Utility
                 [com.cognitect/transit-clj "0.8.300"]
                 [manifold "0.1.6"]
                 [byte-streams "0.2.2"]

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
                 [org.clojure/clojurescript "1.9.495"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [reagent "0.6.1"]
                 [re-frame "0.9.2"]
                 [day8.re-frame/http-fx "0.1.3"]
                 [cljs-ajax "0.5.9"]]

  :source-paths ["src/clj" "src/cljc"]
  :test-paths ["test/clj"]
  :plugins [[com.palletops/uberimage "0.4.1"]
            [lein-cljsbuild "1.1.5"]
            [cider/cider-nrepl "0.14.0"]
            [org.clojure/tools.nrepl "0.2.12"]
            [lein-figwheel "0.5.9"]]
;;  :hooks [leiningen.cljsbuild]
  :profiles {:dev {:source-paths ["dev"]
                   :target-path "target/dev"
                   :dependencies [[org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [figwheel-sidecar "0.5.9"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :cljsbuild
                   {:builds
                    {:dev-game
                     {:figwheel {:on-jsload "milo.client.game.core/reinitialize"
                                 :websocket-host "192.168.1.141"}
                      :compiler {:main "milo.client.game.core"
                                 :asset-path "js"
                                 :optimizations :none
                                 :source-map true
                                 :source-map-timestamp true}}
                     :production-game
                     {:compiler {:optimizations :advanced
                                 :elide-asserts true
                                 :pretty-print false}}
                     :dev-menu
                     {:figwheel {:on-jsload "milo.client.core/refresh"
                                 :websocket-host "192.168.1.141"}
                      :compiler {:main "milo.client.core"
                                 :asset-path "menu/js"
                                 :optimizations :none
                                 :source-map true
                                 :source-map-timestamp true}}
                     :production-menu
                     {:compiler {:optimizations :advanced
                                 :elide-asserts true
                                 :pretty-print false}}}}}}
  :cljsbuild {:builds {:dev-game {:source-paths ["src-cljs-game"]
                                  :compiler {:output-dir "resources/public/game/js"
                                             :output-to "resources/public/game/game.js"}}
                       :production-game {:source-paths ["src-cljs-game"]
                                         :compiler {:output-dir "target/game"
                                                    :output-to "resources/public/game/game.js"}}
                       :dev-menu {:source-paths ["src-cljs-menu"]
                                  :compiler {:output-dir "resources/public/menu/js"
                                             :output-to "resources/public/menu.js"}}
                       :production-menu {:source-paths ["src-cljs-menu"]
                                         :compiler {:output-dir "target/menu"
                                                    :output-to "resources/public/menu.js"}}}}
  :figwheel {:repl false
             :http-server-root "public"}
  :clean-targets ^{:protect false} ["resources/public/game/game.js"
                                    "resources/public/game/js"
                                    "resources/public/menu.js"
                                    "resources/public/menu/js"]
  :aliases {"production-client" ["do" "clean"
                                 ["cljsbuild" "once" "production-menu"]
                                 ["cljsbuild" "once" "production-game"]]})

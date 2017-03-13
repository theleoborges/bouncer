(defproject bouncer "1.0.1"
  :description "A validation library for Clojure apps"
  :url "http://github.com/leonardoborges/bouncer"
  :license {:name "MIT License"
            :url  "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.495"]
                 [clj-time "0.13.0"]
                 [com.andrewmcveigh/cljs-time "0.5.0-alpha2"]]

  :profiles {:dev  {}
             :1.6  {:jdependencies [[org.clojure/clojure "1.6.0"]]}
             :cljs {:plugins    [[lein-cljsbuild "1.1.5"]
                                 [lein-doo "0.1.7"]]
                    :doo        {:build "test"}
                    :cljsbuild
                                {:builds
                                 {:test
                                  {:source-paths ["src" "test"]
                                   :compiler     {:main          bouncer.runner
                                                  :output-to     "target/test/core.js"
                                                  :target        :nodejs
                                                  :optimizations :none
                                                  :source-map    true
                                                  :pretty-print  true}}}}
                    :prep-tasks [["cljsbuild" "once"]]
                    :hooks      [leiningen.cljsbuild]}}
  ;; TODO: Update travis configuration so cljs tests run alongside JVM tests
  :aliases {"clj-tests" ["with-profile" "1.6:dev" "test"]
            "cljs-tests" ["with-profile" "cljs" "doo" "node" "once"]
            "cljs-auto" ["with-profile" "cljs" "cljsbuild" "auto"]
            "cljs-once" ["with-profile" "cljs" "cljsbuild" "once"]})

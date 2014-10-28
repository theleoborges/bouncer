(defproject bouncer "0.3.2-SNAPSHOT"
  :description "A validation DSL for Clojure apps"
  :url "http://github.com/leonardoborges/bouncer"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :jar-exclusions [#"\.cljx"]
  :dependencies [[org.clojure/clojure "1.6.0-RC1"]
                 [clj-time "0.8.0"]]
  :plugins [[lein-marginalia "0.7.1"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :dev {:dependencies [[org.clojure/clojurescript "0.0-2371"]
                                  [com.andrewmcveigh/cljs-time "0.2.3"]]
                   :plugins [[com.keminglabs/cljx "0.4.0"]
                             [lein-cljsbuild "1.0.3"]
                             [com.cemerick/clojurescript.test "0.3.1"]]
                   :cljx {:builds [{:source-paths ["src"]
                                    :output-path "target/classes"
                                    :rules :clj}
                                   {:source-paths ["src"]
                                    :output-path "target/classes"
                                    :rules :cljs}
                                   {:source-paths ["test"]
                                    :output-path "target/test-classes"
                                    :rules :clj}
                                   {:source-paths ["test"]
                                    :output-path "target/test-classes"
                                    :rules :cljs}]}
                   :cljsbuild {:test-commands {"phantom" ["phantomjs" :runner "target/testable.js"]}
                               :builds [{:source-paths ["target/classes" "target/test-classes"]
                                         :compiler {:output-to "target/testable.js"
                                                    :libs [""]
                                                    :source-map "target/testable.js.map"
                                                    :optimizations :advanced}}]}}}
  :aliases {"all-tests" ["with-profile" "dev:1.4:1.5:1.6" "test"]}
  :source-paths ["src" "target/classes"]
  :test-paths ["target/test-classes"])

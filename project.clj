(defproject bouncer "0.3.3-SNAPSHOT"
  :description "A validation DSL for Clojure(Script)"
  :url "http://github.com/leonardoborges/bouncer"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.6.0-RC1"]
                 [org.clojure/algo.monads "0.1.0"]
                 [clj-time "0.8.0"]]

  :plugins [[lein-marginalia "0.7.1"]]

  :jar-exclusions [#"\.cljx"]

  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}

             :dev {:plugins [[com.keminglabs/cljx "0.4.0" :exclusions [org.clojure/clojure]]]

                   :cljx {:builds [{:source-paths ["src/cljx"]
                                    :output-path "target/classes"
                                    :rules :cljs}
                                   {:source-paths ["src/cljx"]
                                    :output-path "target/classes"
                                    :rules :clj}]}}}

  :aliases {"all-tests" ["with-profile" "1.4:1.5:1.6" "test"]})

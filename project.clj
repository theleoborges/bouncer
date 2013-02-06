(defproject bouncer "0.2.3-SNAPSHOT"
  :description "A validation DSL for Clojure apps"
  :url "http://github.com/leonardoborges/bouncer"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/algo.monads "0.1.0"]]
  :plugins [[lein-marginalia "0.7.1"]]
  :profiles {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-RC1"]]}})

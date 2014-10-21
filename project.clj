(defproject bouncer "0.3.1-beta1"
  :description "A validation DSL for Clojure apps"
  :url "http://github.com/leonardoborges/bouncer"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0-RC1"]
                 [org.clojure/algo.monads "0.1.0"]]
  :plugins [[lein-marginalia "0.7.1"]]
  :profiles {:1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :aliases {"all-tests" ["with-profile" "1.4:1.5:1.6" "test"]})

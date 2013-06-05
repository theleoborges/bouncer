(ns bouncer.core-test
  (:import java.io.File)
  (:use clojure.test [bouncer.validators :only [defvalidator]])
  (:require [bouncer
             [core :as core]
             [validators :as v]]))

(deftest validations
  (testing "Required validations"
    (is (not (core/valid? {}
                          :name v/required)))
    (is (not (core/valid? {:name ""}
                          :name v/required)))
    (is (not (core/valid? {:name nil}
                          :name v/required)))
    (is (core/valid? {:name "Leo"}
                     :name v/required)))

  (testing "Number validations"
    ;; map entries are optional by default...
    (is (core/valid? {}
                     :age v/number))
    ;;unless otherwise specified
    (is (not (core/valid? {}
                          :age [v/required v/number])))
    (is (not (core/valid? {:age "invalid"}
                          :age [v/number v/positive])))
    (is (core/valid? {:age nil}
                     :age v/number))
    (is (core/valid? {:age 10}
                     :age v/number)))

  (testing "Custom validations"
    (is (not (core/valid? {}
                          :name (v/custom #((complement nil?) %)))))
    (is (core/valid? {:name "Leo"}
                     :name (v/custom #((complement nil?) %))))

    (letfn [(not-nil [v] ((complement nil?) v))]
      (is (not (core/valid? {}
                            :name (v/custom not-nil))))
      (is (core/valid? {:name "Leo"}
                       :name (v/custom not-nil))))))

(def map-no-street {:address {:street nil :country "Brazil"}})
(def map-with-street (assoc-in map-no-street [:address :street]
                               "Rock 'n Roll Boulevard"))

(deftest nested-maps
  (testing "nested validations"
    (is (not (core/valid? map-no-street
                          :address v/required
                          [:address :street] v/required)))

    (is (not (core/valid? map-no-street
                          [:address :street] v/required)))

    (is (core/valid? map-with-street
                     :address v/required
                     [:address :street] v/required))

    (is (core/valid? map-with-street
                          [:address :street] v/required))

    (is (not (core/valid? {}
                          [:address :street] v/required))))


  (testing "optional nested validations"
    (is (core/valid? {:passport {:issue-year 2012}}
                     [:passport :issue-year] v/number))

    (is (core/valid? {:passport {:issue-year nil}}
                     [:passport :issue-year] v/number))

    (is (not (core/valid? {:passport {:issue-year nil}}
                          [:passport :issue-year] [v/required v/number])))))


(defn first-error-for [key validation-result]
  (-> validation-result (first) key (first)))

(deftest validation-messages
  (testing "default messages"
    (is (= {
            :dob '("Custom validation failed for dob")
            :year '("year must be a number")
            :name '("name must be present")
            :age '("age must be a positive number")
            }

           (first (core/validate {:age -1 :year ""}
                                 :name v/required
                                 :year v/number
                                 :age v/positive
                                 :dob (v/custom #(not (nil? %))))))))

  (testing "custom messages"
    (is (= {
            :age '("Idade deve ser maior que zero")
            :year '("Ano eh obrigatorio")
            :name '("Nome eh obrigatorio")
            :dob  '("Nao pode ser nulo")
            }
           (first
            (core/validate {:age -1 :year ""}
                           :name (v/required :message "Nome eh obrigatorio")
                           :year (v/required :message "Ano eh obrigatorio")
                           :age (v/positive :message "Idade deve ser maior que zero")
                           :dob (v/custom #(not (nil? %)) :message "Nao pode ser nulo")))))))

(deftest validation-result
  (testing "invalid results"
    (let [[result map] (core/validate {:age -1 :year ""}
                                      :name v/required
                                      :year [v/required v/number]
                                      :age v/positive)]
      (is (= result (::core/errors map)))))


  (testing "valid results"
    (let [[result map] (core/validate {:name "Leo"}
                                      :name v/required)]
      (is (true? (and (empty? result)
                      (nil? (::core/errors map))))))))

(deftest coll-validations
  (let [valid-map   {:name "Leo" :pets [{:name "Aragorn"} {:name "Gandalf"}]}
        invalid-map {:name "Leo" :pets [{:name nil} {:name "Gandalf"}]}]

    (testing "nested colls"
      (is (core/valid? valid-map
                       :pets (v/every #(not (nil? (:name %))))))



      (is (not (core/valid? invalid-map
                            :pets (v/every #(not (nil? (:name %))))))))

    (testing "default messages for nested colls"
      (let [[result map] (core/validate invalid-map
                                        :pets (v/every #(not (nil? (:name %)))))]
        (is (= "All items in pets must satisfy the predicate"
               (-> result :pets (first))))))

    (testing "custom messages for nested colls"
      (let [[result map] (core/validate invalid-map
                                        :pets (v/every #(not (nil? (:name %)))
                                                          :message "All pets must have names"))]
        (is (= "All pets must have names"
               (-> result :pets (first)))))))


  (testing "deep nested coll"
    (is (core/valid? {:name "Leo"
                      :address {:current { :country "Australia"}
                                :past [{:country "Spain"} {:country "Brasil"}]}}
                     [:address :past] (v/every #(not (nil? (:country %))))))

    (is (not (core/valid? {:name "Leo"
                           :address {:current { :country "Australia"}
                                     :past [{:country "Spain"} {:country nil}]}}
                          [:address :past] (v/every #(not (nil? (:country %)))))))))


(defvalidator directory
  {:default-message-format "%s must be a valid directory" :optional false}
  [path]
  (.isDirectory ^File (clojure.java.io/file path)))

(defvalidator readable
  {:default-message-format "%s is not readable" :optional false}
  [path]
  (.canRead ^File (clojure.java.io/file path)))

(defvalidator writeable
  {:default-message-format "%s is not writeable" :optional false}
  [path]
  (.canRead ^File (clojure.java.io/file path)))

(deftest early-exit
  (testing "short circuit validations for single entry"
    (is (= {:age '("age must be present")}
           (first (core/validate {}
                                 :age [v/required v/number v/positive]))))

    (is (= {:age '("age must be present")}
           (first (core/validate {:age ""}
                                 :age [v/required v/number v/positive]))))

    (is (= {:age '("age must be a number")}
           (first (core/validate {:age "NaN"}
                                 :age [v/required v/number v/positive]))))

    (is (= {:age '("age must be a positive number")}
           (first (core/validate {:age -7}
                                 :age [v/required v/number v/positive]))))

    (let [config-params {:input-dir "some/directory/path"
                         :output-dir "some/other/directory/path"}]
      (is (= {
              :output-dir '("output-dir must be a valid directory")
              :input-dir  '("input-dir must be a valid directory")
              }
             (first (core/validate config-params
                                   :input-dir [v/required directory readable]
                                   :output-dir [v/required directory writeable])))))))

(deftest all-validations
  (testing "all built-in validators"
    (let [errors-map {
                      :age    '("age must be present")
                      :mobile '("wrong format")
                      :car    '("car must be one of the values in the list")
                      :dob    '("dob must be a number")
                      :name   '("name must be present")
                      :passport {:number '("number must be a positive number")}
                      :address  {:past   '("All items in past must satisfy the predicate")}
                      }
          invalid-map {:name nil
                       :age ""
                       :passport {:number -7 :issued_by "Australia"}
                       :dob "NaN"
                       :address {:current { :country "Australia"}
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate invalid-map
                                   :name v/required
                                   :age v/required
                                   :mobile (v/custom #(string? %) :message "wrong format")
                                   :car (v/member ["Ferrari" "Mustang" "Mini"])
                                   :dob v/number
                                   [:passport :number] v/positive
                                   [:address :past] (v/every #(not (nil? (:country %)))))))))))


(deftest pipelining-validations
  (testing "should preserve the existing errors map if there is one"
    (let [validation-errors (-> {:age "NaN"}
                            (core/validate :name v/required)
                            second
                            (core/validate :age v/number)
                            second
                            ::core/errors)]
      (is (= 2
             (count (select-keys validation-errors [:age :name])))))))

(deftest preconditions
  (testing "runs the current validation only if the pre-condition is met"
    (is (core/valid? {:a 1 :b "Z"}
                     :b (v/member #{"Y" "Z"} :when (comp pos? :a))))

    (is (not (core/valid? {:a 1 :b "X"}
                          :b (v/member #{"Y" "Z"} :when (comp pos? :a)))))

    (is (core/valid? {:a -1 :b "Z"}
                     :b (v/member #{"Y" "Z"} :when (comp pos? :a))))

    (is (core/valid? {:a -1 :b "X"}
                     :b (v/member #{"Y" "Z"} :when (comp pos? :a))))

    (is (not (core/valid? {:a 1 :b "Z"}
                          :b (v/member #{"Y" "Z"} :when (comp pos? :a))
                          :c v/required)))))

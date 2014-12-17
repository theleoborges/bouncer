(ns bouncer.validators-test
  (:require [bouncer.core :as core]
            [bouncer.validators :as v]
            #+clj [clojure.test :refer [deftest testing is]]
            #+clj [clj-time.format :as f]
            #+cljs [cemerick.cljs.test :as t]
            #+cljs [cljs-time.format :as f])
  #+cljs (:require-macros [cemerick.cljs.test :refer [is deftest testing]]))

(def addr-validator-set
  {:postcode [v/required v/number]
   :street    v/required
   :country   v/required
   :past      [[v/every #(not (nil? (:country %)))]]})

(def addr-validator-set+custom-messages
  {:postcode [[v/required :message "required"] [v/number :message "number"]]
   :street    [[v/required :message "required"]]
   :country   [[v/required :message "required"]]
   :past      [[v/every #(not (nil? (:country %))) :message "every"]]})

(def address-validator
  {:postcode v/required})

(def person-validator
  {:name v/required
   :age [v/required v/number]
   :address address-validator})

(def deep-validator
  {:winner person-validator})

(def default-validate (partial core/validate core/with-default-messages))

(deftest validator-sets
  (testing "validator sets for nested maps"
    (is (core/valid? {:address {:postcode 2000
                                  :street   "Crown St"
                                  :country  "Australia"
                                  :past [{:country "Spain"} {:country "Brazil"}]}}
                     :address addr-validator-set))

    (is (not (core/valid? {}
                       :address addr-validator-set)))

    (let [errors-map {:address {
                                :postcode '("postcode must be present")
                                :street   '("street must be present")
                                :country  '("country must be present")
                                :past '("All items in past must satisfy the predicate")
                                }}
          invalid-map {:address {:postcode ""
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (default-validate invalid-map
                                   :address addr-validator-set))))))

  (testing "custom messages in validator sets"
    (let [errors-map {:address {
                                :postcode '("required")
                                :street    '("required")
                                :country   '("required")
                                :past '("every")
                                }}
          invalid-map {:address {:postcode ""
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (default-validate invalid-map
                                   :address addr-validator-set+custom-messages))))))

  (testing "validator sets and standard validators together"
    (let [errors-map {:age '("required")
                      :name '("name must be present")
                      :passport {:number '("number must be a positive number")}
                      :address {
                                :postcode '("postcode must be a number")
                                :street    '("street must be present")
                                :country   '("country must be present")
                                :past '("All items in past must satisfy the predicate")
                                }}
          invalid-map {:name nil
                       :age ""
                       :passport {:number -7 :issued_by "Australia"}
                       :address {:postcode "NaN"
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (default-validate invalid-map
                                   :name v/required
                                   :age [[(complement empty?) :message "required"]]
                                   [:passport :number] v/positive
                                   :address addr-validator-set))))))

  (testing "composing validator sets at the top level"
    (is (core/valid? {:address {:postcode 2000}
                      :name "Leo"
                      :age 29}
                     person-validator))

    (is (not (core/valid? {}
                          person-validator)))

    (let [errors-map {:address {:postcode '("postcode must be present")}
                      :name '("name must be present")
                      :age  '("age must be present")}]
      (is (= errors-map
             (first (default-validate {}
                                   person-validator)))))

    ;; Test for issue #7 on github
    (let [errors-map {:winner {:address {:postcode '("postcode must be present")}
                               :name '("name must be present")
                               :age  '("age must be present")}}]
      (is (= errors-map
             (first (default-validate {}
                                   deep-validator)))))))


(def valid-items #{:a :b :c})

(def items-validator-set
  {:field1 [[v/member valid-items]]})

;; addresses Issue https://github.com/leonardoborges/bouncer/issues/5
(deftest nested-symbols
  (testing "validator sets supports symbols -which are to be resolved- as arguments to validator functions"
    (is (not (core/valid? {:field1 :e}
                          items-validator-set)))
    (is (core/valid? {:field1 :a}
                          items-validator-set))))



(deftest range-validator
  (testing "presence of value in the given range"
    (is (core/valid? {:age 4}
                  :age [[v/member (range 5)]]))
    (is (not (core/valid? {:age 5}
                          :age [[v/member (range 5)]])))))

(deftest regex-validator
  (testing "matching the given pattern"
    (is (core/valid? {:phone "555"}
                  :phone [[v/matches #"^\d+$"]]))
    (is (not (core/valid? {:phone "NaN"}
                  :phone [[v/matches #"^\d+$"]])))))

(deftest email-validator
  (testing "can match typical legal emails"
    (is (core/valid? {:email "test@googlexyz.com"} :email [[v/email]]))
    (is (core/valid? {:email "test+blabla@googlexyz.com"} :email [[v/email]]))
    (is (core/valid? {:email "test@googlexyz.co.uk"} :email [[v/email]])))
  (testing "will reject invalid emails"
    (is (not (core/valid? {:email nil} :email [[v/email]])))
    (is (not (core/valid? {:email ""} :email [[v/email]])))
    (is (not (core/valid? {:email "test"} :email [[v/email]])))
    (is (not (core/valid? {:email "test@"} :email [[v/email]])))
    (is (not (core/valid? {:email "test@googlexyz"} :email [[v/email]])))
    (is (not (core/valid? {:email "@google.xyz.com"} :email [[v/email]])))))

(def y-m (f/formatters :year-month))

(deftest datetime-validator
  (testing "matched without custom formatter"
    (is (core/valid? {:dt "2014-04-02"} :dt [[v/datetime]]))
    (is (core/valid? {:dt "2014-04-02 03:03:03"} :dt [[v/datetime]])))
  (testing "rejected without custom formatter"
    (is (not (core/valid? {:dt "2014/04/01"} :dt [[v/datetime]]))))
  (testing "matched with custom formatter"
    (is (core/valid? {:dt "2014/04/01"} :dt [[v/datetime "yyyy/MM/dd"]])))
  (testing "valid date rejected because of specific clj-time formatter"
    (is (not (core/valid? {:dt "2014-01-02"} :dt [[v/datetime y-m]]))))
  (testing "matched by specific clj-time formatter"
    (is (core/valid? {:dt "2014-01"} :dt [[v/datetime y-m]]))))

(deftest max-length-validator
  (testing "enforcing a maximum value"
    (is (core/valid? {:first-name "First Name"} :first-name [[v/max-length 10]]))
    (is (not (core/valid? {:first-name "First Name"} :first-name [[v/max-length 9]])))
    (is (not (core/valid? {:not-a-string 25} :not-a-string [[v/max-length 10]])))
    (is (not (core/valid? {:first-name "First Name"} :first-name [[v/max-length "invalid-max"]])))))

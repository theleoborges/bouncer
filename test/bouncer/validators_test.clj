(ns bouncer.validators-test
  (:use clojure.test)
  (:use [bouncer.validators :only [defvalidator defvalidatorset]])
  (:require [bouncer
             [core :as core]
             [validators :as v]]))

(defvalidatorset addr-validator-set
  :postcode [v/required v/number]
  :street    v/required
  :country   v/required
  :past      (v/every #(not(nil? (:country %)))))

(defvalidatorset addr-validator-set+custom-messages
  :postcode [(v/required :message "required") (v/number :message "number")]
  :street    (v/required :message "required")
  :country   (v/required :message "required")
  :past      (v/every #(not(nil? (:country %))) :message "every"))


(defvalidatorset address-validator
  :postcode v/required)

(defvalidatorset person-validator
  :name v/required
  :age [v/required v/number]
  :address address-validator)


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
                                :street    '("street must be present")
                                :country   '("country must be present")
                                :past '("All items in past must satisfy the predicate")
                                }}
          invalid-map {:address {:postcode ""
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate invalid-map
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
             (first (core/validate invalid-map
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
             (first (core/validate invalid-map
                                   :name v/required
                                   :age (v/custom (complement empty?) :message "required")
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
             (first (core/validate {}
                                   person-validator)))))))



(deftest range-validator
  (testing "presence of value in the given range"
    (is (core/valid? {:age 4}
                  :age (v/member (range 5))))
    (is (not (core/valid? {:age 5}
                          :age (v/member (range 5)))))))

(deftest regex-validator
  (testing "matching the given pattern"
    (is (core/valid? {:phone "555"}
                  :phone (v/matches #"^\d+$")))
    (is (not (core/valid? {:phone "NaN"}
                  :phone (v/matches #"^\d+$"))))))
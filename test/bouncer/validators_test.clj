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


(deftest validator-sets
  (testing "composable validators"
    (let [errors-map {:address {
                                :postcode '("postcode must be a number"
                                            "postcode must be present")
                                :street    '("street must be present")
                                :country   '("country must be present")
                                :past '("All items in past must satisfy the predicate")
                                }}
          invalid-map {:address {:postcode ""
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate invalid-map
                                   :address addr-validator-set))))))
  
  (testing "validator sets and standard validators"
    (let [errors-map {:age '("age isn't 29" "age must be a number" "age must be present")
                      :name '("name must be present")
                      :passport {:number '("number must be a positive number")}
                      :address {
                                :postcode '("postcode must be a number"
                                            "postcode must be present")
                                :street    '("street must be present")
                                :country   '("country must be present")
                                :past '("All items in past must satisfy the predicate")
                                }}
          invalid-map {:name nil
                       :age ""
                       :passport {:number -7 :issued_by "Australia"}
                       :address {:postcode ""
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate invalid-map
                                   :name v/required
                                   :age [v/required
                                         v/number
                                         (v/custom #(= 29 %) :message "age isn't 29")]
                                   [:passport :number] v/positive 
                                   :address addr-validator-set)))))))
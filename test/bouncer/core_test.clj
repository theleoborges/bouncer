(ns bouncer.core-test
  (:use clojure.test)
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
            :year '("year must be a number" "year must be present")
            :name '("name must be present")
            :age '("age must be a positive number")
            }
           
           (first (core/validate {:age -1 :year ""}
                                 :name v/required
                                 :year [v/required v/number]
                                 :age v/positive
                                 :dob (v/custom #(not (nil? %))))))))

  (testing "custom messages"
    (is (= {
            :age '("Idade deve ser maior que zero")
            :year '("Ano deve ser um numero" "Ano eh obrigatorio")
            :name '("Nome eh obrigatorio")
            :dob  '("Nao pode ser nulo")
            }
           (first (core/validate {:age -1 :year ""}
                                 :name (v/required :message "Nome eh obrigatorio")
                                 :year [(v/required :message "Ano eh obrigatorio")
                                        (v/number :message "Ano deve ser um numero")]
                                 :age (v/positive :message "Idade deve ser maior que zero")
                                 :dob (v/custom #(not (nil? %)) :message "Nao pode ser nulo"))
                  )))))

(deftest validation-result
  (testing "invalid results"
    (let [[result map] (core/validate {:age -1 :year ""}
                                      :name v/required
                                      :year [v/required v/number]
                                      :age v/positive)]
      (is (= result (:errors map)))))


  (testing "valid results"
    (let [[result map] (core/validate {:name "Leo"}
                                      :name v/required)]
      (is (true? (and (empty? result)
                      (nil? (:errors map))))))))


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

(deftest all-validations
  (testing "all built-in validators"
    (let [errors-map {
                      :age '("age out of range" "age isn't 29" "age must be a number" "age must be present")
                      :name '("name must be present")
                      :passport {:number '("number must be a positive number")}
                      :address {:past '("All items in past must satisfy the predicate")}
                      }
          invalid-map {:name nil
                       :age ""
                       :passport {:number -7 :issued_by "Australia"}
                       :address {:current { :country "Australia"}
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate invalid-map
                                   :name v/required
                                   :age [v/required
                                         v/number
                                         (v/custom #(= 29 %) :message "age isn't 29")
                                         (v/member (range 5))]
                                   [:passport :number] v/positive 
                                   [:address :past] (v/every #(not (nil? (:country %)))))))))))

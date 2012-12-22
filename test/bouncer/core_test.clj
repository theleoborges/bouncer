(ns bouncer.core-test
  (:use clojure.test)
  (:require [bouncer.core :as core]))

(deftest validations
  (testing "Required validations"
    (is (not (core/valid? {}
                          (core/required :name))))
    (is (not (core/valid? {:name ""}
                          (core/required :name))))
    (is (not (core/valid? {:name nil}
                          (core/required :name))))
    (is (core/valid? {:name "Leo"}
                     (core/required :name))))

  (testing "Number validations"
    ;; map entries are optional by default...
    (is (core/valid? {}
                     (core/number :age)))
    ;;unless otherwise specified
    (is (not (core/valid? {}
                          (core/required :age)
                          (core/number :age))))
    (is (not (core/valid? {:age ""}
                          (core/number :age))))
    (is (core/valid? {:age nil}
                     (core/number :age)))
    (is (core/valid? {:age 10}
                     (core/number :age)))))

(def map-no-street {:address {:street nil :country "Brazil"}})
(def map-with-street (assoc-in map-no-street [:address :street]
                               "Rock 'n Roll Boulevard"))

(deftest nested-maps
  (testing "nested validations"
    (is (not (core/valid? map-no-street
                          (core/required :address)
                          (core/required [:address :street]))))
    
    (is (not (core/valid? map-no-street
                          (core/required [:address :street]))))
    
    (is (core/valid? map-with-street
                     (core/required :address)
                     (core/required [:address :street])))
    
    (is (core/valid? map-with-street
                     (core/required [:address :street])))

    (is (not (core/valid? {}
                     (core/required [:address :street])))))


  (testing "optional nested validations"
    (is (core/valid? {:passport {:issue-year 2012}}
                     (core/number [:passport :issue-year])))

    (is (core/valid? {:passport {:issue-year nil}}
                     (core/number [:passport :issue-year])))
    
    (is (not (core/valid? {:passport {:issue-year nil}}
                          (core/required [:passport :issue-year])
                          (core/number [:passport :issue-year]))))))

;;(require '[bouncer.core :as core] :reload)

(defn first-error-for [key validation-result]
  (-> validation-result (first) key (first)))

(deftest validation-messages
  (testing "default messages"
    (is (= "name must be present" (first-error-for :name
                                                   (core/validate {}
                                                                  (core/required :name)))))

    (is (= "age must be present" (first-error-for :age
                                                   (core/validate {}
                                                                  (core/required :age)))))
    (is (= "age must be a number" (first-error-for :age
                                                   (core/validate {:age ""}
                                                                  (core/number :age)))))

    (is (= "age must be a positive number" (first-error-for :age
                                                   (core/validate {:age -7}
                                                                  (core/positive :age)))))

    (is (= {
            :age '("age must be a number" "age must be present")
            :name '("name must be present")
            }
           (first (core/validate {:age ""}
                                 (core/required :name)
                                 (core/required :age)                              
                                 (core/number :age))))))


  (testing "custom messages"
    (is (= {
            :age '("Idade deve ser maior que zero")
            :year '("Ano deve ser um numero" "Ano eh obrigatorio")
            :name '("Nome eh obrigatorio")
            }
           (first (core/validate {:age -1 :year ""}
                                 (core/required :name "Nome eh obrigatorio")
                                 (core/required :year "Ano eh obrigatorio")
                                 (core/number :year "Ano deve ser um numero")
                                 (core/positive :age "Idade deve ser maior que zero")))))))

(deftest validation-result
  (testing "invalid results"
    (let [[result map] (core/validate {:age -1 :year ""}
                                 (core/required :name)
                                 (core/required :year)
                                 (core/number :year)
                                 (core/positive :age))]
      (is (= result (:errors map)))))


  (testing "valid results"
    (let [[result map] (core/validate {:name "Leo"} (core/required :name))]
      (is (true? (and (empty? result)
                      (nil? (:errors map))))))))
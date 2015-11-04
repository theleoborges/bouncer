(ns bouncer.core-test
  (:require [bouncer.core :as core]
            #+clj [clojure.test :refer [is deftest testing are]]
            #+clj [bouncer.validators :as v :refer [defvalidator]]
            #+cljs [cemerick.cljs.test :as t]
            #+cljs [bouncer.validators :as v])
  #+cljs (:require-macros [cemerick.cljs.test :refer [is deftest testing are]]
                          [bouncer.validators :refer [defvalidator]]))

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
                          :name (complement nil?))))
    (is (core/valid? {:name "Leo"}
                     :name (complement nil?)))

    (letfn [(not-nil [v] ((complement nil?) v))]
      (is (not (core/valid? {}
                            :name not-nil)))
      (is (core/valid? {:name "Leo"}
                       :name not-nil)))))


(deftest standard-functions
  (testing "it can use plain clojure functions as validators"
    (are [valid? subject    validations] (= valid? (core/valid? subject validations))
         false   {:age 0}   {:age [[pos? :message "positive"]]}
         true    {:age  10} {:age [[pos? :message "positive"]]}
         false   {:age 0}   {:age pos?}
         true    {:age  10} {:age pos?})))

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
                    :dob (complement nil?))))))

  (testing "custom messages"
    (is (= {
            :age '("Idade deve ser maior que zero")
            :year '("Ano eh obrigatorio")
            :name '("Nome eh obrigatorio")
            :dob  '("Nao pode ser nulo")
            }
           (first (core/validate {:age -1 :year ""}
                    :name [[v/required :message "Nome eh obrigatorio"]]
                    :year [[v/required :message "Ano eh obrigatorio"]]
                    :age [[v/positive :message "Idade deve ser maior que zero"]]
                    :dob [[(complement nil?) :message "Nao pode ser nulo"]]))))))

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
                       :pets [[v/every #(not (nil? (:name %)))]]))

      (is (not (core/valid? invalid-map
                            :pets [[v/every #(not (nil? (:name %)))]]))))

    (testing "default messages for nested colls"
      (let [[result map] (core/validate invalid-map
                           :pets [[v/every #(not (nil? (:name %)))]])]
        (is (= "All items in pets must satisfy the predicate"
               (-> result :pets (first))))))

    (testing "custom messages for nested colls"
      (let [[result map] (core/validate invalid-map
                           :pets [[v/every #(not (nil? (:name %)))
                                   :message "All pets must have names"]])]
        (is (= "All pets must have names"
               (-> result :pets (first)))))))


  (testing "deep nested coll"
    (is (core/valid? {:name "Leo"
                      :address {:current { :country "Australia"}
                                :past [{:country "Spain"} {:country "Brasil"}]}}
                     [:address :past] [[v/every #(not (nil? (:country %)))]]))

    (is (not (core/valid? {:name "Leo"
                           :address {:current { :country "Australia"}
                                     :past [{:country "Spain"} {:country nil}]}}
                          [:address :past] [[v/every #(not (nil? (:country %)))]])))))


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
                    :age [v/required v/number v/positive]))))))


(defn pred-fn [x]
  (not (nil? (:country x))))

(deftest all-validations
  (testing "all built-in validators"
    (let [errors-map {
                      :age    '({:path [:age], :value "", :args nil, :message nil
                                 :metadata {:default-message-format "%s must be present"
                                            :optional false
                                            :validator :bouncer.validators/required}})
                      :mobile '({:path [:mobile], :value nil, :args nil, :message "wrong format"
                                 :metadata {:default-message-format "Custom validation failed for %s"
                                            :optional false}})
                      :car    '({:path [:car], :value "Volvo", :args [["Ferrari" "Mustang" "Mini"]], :message nil
                                 :metadata {:default-message-format "%s must be one of the values in the list"
                                            :optional true
                                            :validator :bouncer.validators/member}})
                      :dob    '({:path [:dob], :value "NaN", :args nil, :message nil
                                 :metadata {:default-message-format "%s must be a number"
                                            :optional true
                                            :validator :bouncer.validators/number}})
                      :name   '({:path [:name], :value nil, :args nil, :message nil
                                 :metadata {:default-message-format "%s must be present"
                                            :optional false
                                            :validator :bouncer.validators/required}})
                      :passport {:number '({:path [:passport :number], :value -7, :args nil, :message nil
                                            :metadata {:default-message-format "%s must be a positive number"
                                                       :optional true
                                                       :validator :bouncer.validators/positive}})}
                      :address  {:past   (list {:path [:address :past], :value [{:country nil} {:country "Brasil"}],
                                                :args [pred-fn] :message nil
                                                :metadata {:default-message-format "All items in %s must satisfy the predicate"
                                                           :optional true
                                                           :validator :bouncer.validators/every}})}
                      }
          invalid-map {:name nil
                       :age ""
                       :car "Volvo"
                       :passport {:number -7 :issued_by "Australia"}
                       :dob "NaN"
                       :address {:current { :country "Australia"}
                                 :past [{:country nil} {:country "Brasil"}]}}]
      (is (= errors-map
             (first (core/validate identity
                                   invalid-map
                                   :name v/required
                                   :age v/required
                                   :mobile [[string? :message "wrong format"]]
                                   :car [[v/member ["Ferrari" "Mustang" "Mini"]]]
                                   :dob v/number
                                   [:passport :number] v/positive
                                   [:address :past] [[v/every pred-fn]])))))))


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
                     :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]]))

    (is (not (core/valid? {:a 1 :b "X"}
                          :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]])))

    (is (core/valid? {:a -1 :b "Z"}
                     :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]]))

    (is (core/valid? {:a -1 :b "X"}
                     :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]]))

    (is (not (core/valid? {:a 1 :b "Z"}
                          :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]]
                          :c v/required)))))

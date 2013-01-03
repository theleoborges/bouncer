(ns bouncer.core
  (require [clojure.algo.monads :as m]))

(defn mk-validator
  "Returns a validation function that will use (pred (k m)) to determine if m is valid. msg will be added to the :errors entry in invalid scenarios.

If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

A validator can also be marked optional, in which case the validation will only run if k has a value in m. e.g.:

  (mk-validator number? k msg :optional true)"
  ([pred k msg & {optional :optional}]
     (fn [m]
       (if (and optional (cond
                          (vector? k) (not (get-in m k))
                          (keyword? k) (not (k m))))
         ((mk-validator (fn [_] true) k msg) m)
         ((mk-validator pred k msg) m))))
  ([pred k msg]
     (fn [m]
       (letfn [(validate* [value path]
                 (if (not (pred value))
                   (let [new-state (update-in m path #(conj % msg))]
                     [(:errors new-state) new-state])
                   [(:errors m #{}) m]))]
         (cond
          (vector? k) (validate* (get-in m k) (cons :errors k))
          (keyword? k) (validate* (k m) [:errors k]) )))))


(defn- key->name [k]
  (cond
   (vector? k) (name (peek k))
   (keyword? k) (name k)))

;; Default validators
(defn required
  "Returns a validation function that checks k has a value the map being validated.
If the value is a string, it makes sure it's not empty, otherwise it checks for nils.

If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

If no message is given, a default message will be used"
  ([k]
     (required k :message (format "%s must be present" (key->name k))))
  ([k & {message :message}]
     (mk-validator #(if (string? %)
                      (not (empty? %)) 
                      (not (nil? %))) k message)))

(defn number
  "Returns a validation function that checks the value for k is a number.

If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

If no message is given, a default message will be used"
  ([k]
     (number k :message (format "%s must be a number" (key->name k))))
  ([k & {message :message}]
     (mk-validator number? k message :optional true)))

(defn positive
  "Returns a validation function that checks the value for k is greater than zero.

If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

If no message is given, a default message will be used"
  ([k]
     (positive k :message (format "%s must be a positive number" (key->name k))))
  ([k & {message :message}]
     (mk-validator #(and (number? %)
                         (> % 0)) k message :optional true)))

(defn every
  "Returns a validation function that checks pred for every item in the collection at key k.

pred can be any function that yields a boolean. It will be invoked for every item in the collection.

If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

If no message is given, a default message will be used"
  ([k pred]
     (every k pred :message (format "All items in %s must satisfy the predicate" (key->name k))))
  ([k pred & {message :message}]
     (mk-validator #(every? pred %) k message)))

(defmacro validate*
  "Validates the map m using the validation functions fs.
Returns a vector where the first element is the map of validation errors if any and the second is the original map (possibly)augmented with the errors map."
  [m & fs]
  (let [ignore (gensym "ignore__")
        result (gensym "result__")
        fns-pairs (vec (interleave (repeat (count fs) ignore) fs))]
    `((m/domonad m/state-m
                 ~(assoc fns-pairs (- (count fns-pairs) 2) result)
                 ~result) ~m)))

(defmacro valid? [& args]
  "Takes a map and one or more validation functions. Returns true if the map passes all validations. False otherwise"
  `(empty? (first (validate ~@args))))



;; (core/validate invalid-map
;;                (core/required :name)
;;                (core/required :age)
;;                (core/number :age)
;;                (core/positive [:passport :number])
;;                (core/every [:address :past]
;;                            #(not (nil? (:country %)))))


;; (core/validate invalid-map
;;                :name core/required
;;                :age  [core/required :message "custom"
;;                       core/number   :message "custom"]
;;                [:passport :number] core/positive
;;                [:address :past]    [core/every #(not (nil? (:country %)))]
;;                )

;; (def my-rest [:name 'required
;;               :age  ['required :message "custom"
;;                      'number   :message "custom"]
;;               [:passport :number] 'positive])



;; (let [[key fn-or-vec & rest] my-rest]
;;   (prn "key: " key " - fn-or-vec: " fn-or-vec))

;; (for [rule (partition 2 my-rest)
;;       :let [[key-or-vec fn-or-vec] rule]]
;;   (cond
;;    (vector? fn-or-vec) (prn `(something))
;;    :else (prn `(~fn-or-vec ~key-or-vec))))


;; (defn extract-options [v]
;;   (apply hash-map (apply concat
;;                    (take-while #(keyword? (first %))
;;                                (partition 2 v)))))

;; (defn but-options [v]
;;   (flatten
;;    (drop-while #(keyword? (first %))
;;                (partition-all 2 v))))

;; (extract-options [:pred #{} :message "alh" 'required 'number])
;; (extract-options [:pred '#{} :message "alh" 'required :ka])
;; (extract-options [:pred '#(not (nil? (:country %))) :message "alh" 'required 'number])
;; (extract-options [:message "alh" 'required])
;; (extract-options ['required])

;; (but-options [:pred #{} :message "alh" 'required 'number])
;; (but-options [:pred #{} :message "alh" 'required :ka])
;; (but-options [:message "alh" 'required])
;; (but-options ['required])

(defn build-multi-step
  ([key-or-vec fn-vec] (build-multi-step key-or-vec fn-vec []))
  ([key-or-vec [f-or-list & rest] acc]
     (if-not f-or-list
       acc
       (cond
        (list? f-or-list)
        (let [[f & opts] f-or-list]
          (recur key-or-vec
                 rest
                 (conj acc `(~(resolve f) ~key-or-vec ~@opts))))
        :else (recur key-or-vec
                     rest
                     (conj acc `(~(resolve f-or-list) ~key-or-vec)))))))

;;(build-multi-step :name ['required :message "custom" 'number])

(defn build-steps [forms]
  (reduce (fn [acc [key-or-vec fn-coll :as rule]]
            (cond
             (vector? fn-coll) (concat acc (build-multi-step key-or-vec fn-coll))
             (list? fn-coll) (concat acc (build-multi-step key-or-vec [fn-coll]))
             :else (conj acc `(~(resolve fn-coll) ~key-or-vec))))
          []
          (partition 2 forms)))

;;(build-steps my-rest)




(defmacro validate [m & forms]
  `(validate* ~m
             ~@(build-steps forms)))


;; (macroexpand-1 '(validate {}
;;                           :pets [(every #(not (nil? (:name %))))]))

;; (macroexpand-1 '(validate {}
;;                           :pets (every #(not (nil? (:name %))))))

;; (validate {:pets [{:name nil}]}
;;                           :pets (every #(not (nil? (:name %)))))

;; (validate {:pets [{:name nil}]}
;;                        :pets [(every #(not (nil? (:name %))))])

;; (macroexpand-1
;;  '(validate {} :name required
;;                 :age  [(required :message "custom")
;;                        number]
;;                 [:passport :number] (positive :message "positive you idiot!")
;;                 [:address :past] [(every #(not (nil? (:country %)))
;;                                          :message "all past addresses must have a country")]))

;; (validate {} :name required
;;                 :age  [(required :message "custom")
;;                        number]
;;                 [:another :one] required
;;                 [:passport :number] (positive :message "positive you idiot!")
;;                 [:address :past] [(every #(not (nil? (:country %)))
;;                                          :message "all past addresses must have a country")])

;; (valid? {} :name required
;;                 :age  [(required :message "custom")
;;                        number]
;;                 [:another :one] required
;;                 [:passport :number] (positive :message "positive you idiot!")
;;                 [:address :past] [(every #(not (nil? (:country %)))
;;                                          :message "all past addresses must have a country")])

;; (def person {:address {:past [{:country nil} {:country "Brazil"}]}})
;; (new-validate person
;;               :name required
;;               :age  [required :message "custom"
;;                      number]
;;               [:passport :number] positive
;;               [:address :past]    [every #(not (nil? (:country %)))
;;                                          :message "all past addresses must have a country"])



;; (def person {:age "leo"})


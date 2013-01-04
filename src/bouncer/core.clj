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


(defmacro defvalidator
  "Defines a new validating functions using args & body semantics as provided by \"defn\".
  docstring and opts-map are optional

  opts-map is a map of key-value pairs and may be one of:
    :default-message-format used when the client of this validator doesn't provide a message
    :optional whether the validation should be run only if the given key has a non-nil value in the map. Defaults to false.
"
  {:arglists '([name docstring? opts-map? [args*] & body])}
  [name & options]
  (let [docstring (if (string? (first options))
                    (first options)
                    nil)
        options (if (string? (first options))
                  (next options)
                  options)
        {:keys [default-message-format optional]} (if (map? (first options))
                                                    (first options)
                                                    {})
        options (if (map? (first options))
                  (next options)
                  options)
        [args & body] options]
    `(defn ~(with-meta name {:doc docstring})
       {:arglists '([~(symbol 'k) ~(symbol 'opts?)])}
       ([k#]
          (~name k# :message (format (if ~default-message-format
                                       ~default-message-format
                                       "Custom validation failed for %s") (key->name k#))))
       ([k# & {message# :message}]
          (mk-validator (fn ~args
                          ~@body) k# message# :optional ~optional)))))

;; Default validators
(defvalidator required
  "Validating function that checks if its only argument has a value.

  If the value is a string, it makes sure it's not empty, otherwise it checks for nils.

  The resulting function takes a key k.

  If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

  opts is an optional sequence of named arguments and may be one of:
    - :message the message to be added to the :errors map should this validation fail
  
  If no message is given, a default message will be used
"
  {:default-message-format "%s must be present"}
  [value]
  (if (string? value)
    (not (empty? value)) 
    (not (nil? value))))

(defvalidator number
  " Validating function that checks its only argument is a number.

  The resulting function takes a key k.

  If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

  opts is an optional sequence of named arguments and may be one of:
    - :message the message to be added to the :errors map should this validation fail
  
  If no message is given, a default message will be used
"
  {:default-message-format "%s must be a number" :optional true}
  [maybe-a-number]
  (number? maybe-a-number))


(defvalidator positive
  " Validating function that checks its only argument is greater than.

  The resulting function takes a key k.

  If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

  opts is an optional sequence of named arguments and may be one of:
    - :message the message to be added to the :errors map should this validation fail
  
  If no message is given, a default message will be used
"
  {:default-message-format "%s must be a positive number" :optional true}
  [number]
  (and (number? number)
       (> number 0)))

(defn custom
  "Returns a validation function that checks pred for every item in the collection at key k.

pred can be any function that yields a boolean. It will be invoked for every item in the collection.

If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

If no message is given, a default message will be used"
  ([k pred]
     (custom k pred :message (format "Custom validation failed for %s" (key->name k))))
  ([k pred & {message :message}]
     (mk-validator pred k message)))

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

(defn build-steps [forms]
  (reduce (fn [acc [key-or-vec fn-coll :as rule]]
            (cond
             (vector? fn-coll) (concat acc (build-multi-step key-or-vec fn-coll))
             (list? fn-coll) (concat acc (build-multi-step key-or-vec [fn-coll]))
             :else (conj acc `(~(resolve fn-coll) ~key-or-vec))))
          []
          (partition 2 forms)))

(defmacro validate [m & forms]
  "Validates the map m using the validations specified by forms.

  forms is a sequence of key/value pairs where:

  key   ==> :keyword or [:a :path]

  value ==> validation-function or
           (validation-function args+opts) or
           [validation-function another-validation-function] or
           [validation-function (another-validation-function args+opts)]

  e.g.:

  (core/validate a-map
               :name core/required
               :age [core/required
                     (core/number :message \"age must be a number\")]
               [:passport :number] core/positive)

  Returns a vector where the first element is the map of validation errors if any and the second is the original map (possibly)augmented with the errors map.
"
  `(validate* ~m
             ~@(build-steps forms)))
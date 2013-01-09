(ns bouncer.validators
  "This namespace contains all built-in validators as well as
          macros for defining new validators and validator sets"
  {:author "Leonardo Borges"}
  (:require [clojure.walk :as w]
            [bouncer.helpers :as h]))

;; ## Customization support
;;
;; The following functions and macros support creating custom validators

(defn mk-validator
  "Returns a validation function that will use (pred (k m)) to determine if m is valid. msg will be added to the :errors entry in invalid scenarios.

  If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

  A validator can also be marked optional, in which case the validation will only run if k has a value in m.

  e.g.:

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


(defn key->name [k]
  (cond
   (vector? k) (name (peek k))
   (keyword? k) (name k)))


(defmacro defvalidator
  "Defines a new validating function using args & body semantics as provided by \"defn\".
  docstring and opts-map are optional

  opts-map is a map of key-value pairs and may be one of:

  `:default-message-format` used when the client of this validator doesn't provide a message

  `:optional` whether the validation should be run only if the given key has a non-nil value in the map. Defaults to false.

  The function will be called with the value being validated as its first argument.

  Any extra arguments will be passed along to the function in the order they were used in the 
  \"validate\" call.

  e.g.:


    (defvalidator in
      [value coll]
      (some #{value} coll))

    (validate {:age 10}
      :age (in (range 5)))


  This means the validator `in` will be called with the arguments `10` and `(0 1 2 3 4)`, 
  in that order.

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
        [args & body] options
        [pred-subject & rest] args]
    `(defn ~(with-meta name {:doc docstring})
       {:arglists '([~@args])}
       ([k# ~@rest]
          (~name k# ~@rest :message (format (if ~default-message-format
                                              ~default-message-format
                                              "Custom validation failed for %s") (key->name k#))))
       ([k# ~@rest & {message# :message}]
          (mk-validator (fn [~pred-subject]
                          (let [~@(mapcat #(vector %1 %2) rest rest)]
                            ~@body))
                        k# message# :optional ~optional)))))


;; ## Built-in validators

(defvalidator required
  "Validates value is present.

  If the value is a string, it makes sure it's not empty, otherwise it checks for nils.

  For use with validation macros such as `validate` or `valid?`
"
  {:default-message-format "%s must be present"}
  [value]
  (if (string? value)
    (not (empty? value)) 
    (not (nil? value))))

(defvalidator number
  "Validates maybe-a-number is a valid number.

  For use with validation macros such as `validate` or `valid?`"
  {:default-message-format "%s must be a number" :optional true}
  [maybe-a-number]
  (number? maybe-a-number))


(defvalidator positive
  "Validates number is a number and is greater than zero.

  For use with validation macros such as `validate` or `valid?`"
  {:default-message-format "%s must be a positive number" :optional true}
  [number]
  (and (number? number)
       (> number 0)))


(defvalidator member
  "Validates value is a member of coll.

  For use with validation macros such as `validate` or `valid?`"
  {:default-message-format "%s out of range"}
  [value coll]
  (some #{value} coll))

(defvalidator custom
  "Validates pred is true for the given value.

  For use with validation macros such as `validate` or `valid?`"
  [value pred]
  (pred value))

(defvalidator every
  "Validates pred is true for every item in coll.

  For use with validation macros such as `validate` or `valid?`"
  {:default-message-format "All items in %s must satisfy the predicate"}
  [coll pred]
  (every? pred coll)) 

;; ## Composability

(defmacro defvalidatorset
  "Defines a set of validators encapsulating a reusable validation unit.

  forms should follow the semantics of \"bouncer.core/validate\"

  e.g.:

    (defvalidatorset addr-validator-set
      :postcode  [v/required v/number]
      :street    v/required
      :country   v/required)
"
  [name & forms]
  `(def ~(with-meta name {:bouncer-validator-set true})
     '(~@(w/postwalk h/resolve-or-same forms))))
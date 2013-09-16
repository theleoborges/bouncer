(ns bouncer.validators
  "This namespace contains all built-in validators as well as
          macros for defining new validators and validator sets"
  {:author "Leonardo Borges"}
  (:require [clojure.walk :as w]))

;; ## Customization support
;;
;; The following functions and macros support creating custom validators

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


    (defvalidator member
      [value coll]
      (some #{value} coll))

    (validate {:age 10}
      :age [[member (range 5)]])


  This means the validator `member` will be called with the arguments `10` and `(0 1 2 3 4)`, 
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
        {
         :keys [default-message-format optional]
         :or {
              default-message-format "Custom validation failed for %s"
              optional false}
         } (if (map? (first options))
             (first options)
             {})
        options (if (map? (first options))
                  (next options)
                  options)
        [args & body] options
        fn-meta {:default-message-format default-message-format
                 :optional optional}]
    (let [arglists ''([name])]
      `(do (def ~name
             (with-meta (fn ~name 
                          ([~@args]
                             ~@body))
               (merge ~fn-meta)))
           (alter-meta! (var ~name) assoc
                        :doc ~docstring
                        :arglists '([~@args]))))))

;; ## Built-in validators

(defvalidator required
  "Validates value is present.

  If the value is a string, it makes sure it's not empty, otherwise it checks for nils.

  For use with validation functions such as `validate` or `valid?`
"
  {:default-message-format "%s must be present"}
  [value]
  (if (string? value)
    (not (empty? value))
    (not (nil? value))))

(defvalidator number
  "Validates maybe-a-number is a valid number.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be a number" :optional true}
  [maybe-a-number]
  (number? maybe-a-number))


(defvalidator positive
  "Validates number is a number and is greater than zero.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be a positive number" :optional true}
  [number]
  (> number 0))


(defvalidator member
  "Validates value is a member of coll.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be one of the values in the list"}
  [value coll]
  (some #{value} coll))

(defvalidator custom
  "Validates pred is true for the given value.

  For use with validation functions such as `validate` or `valid?`"
  [value pred]
  (println "Warning: bouncer.validators/custom is deprecated and will be removed. Use plain functions instead.")
  (pred value))

(defvalidator every
  "Validates pred is true for every item in coll.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "All items in %s must satisfy the predicate"}
  [coll pred]
  (every? pred coll))

(defvalidator matches
  "Validates value satisfies the given regex pattern.

   For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must satisfy the given pattern" :optional true}
  [value re]
  ((complement empty?) (re-seq re value)))

(defvalidator email
  "Validates value is an email address.

  It implements a simple check to verify there's only a '@' and
  at least one point after the '@'

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be a valid email address"}
  [value]
  (and (required value) (matches value #"^[^@]+@[^@\\.]+[\\.].+")))

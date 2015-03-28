(ns bouncer.validators
  "This namespace contains all built-in validators as well as
          macros for defining new validators and validator sets"
  {:author "Leonardo Borges"}
  #+clj (:require [clj-time.format :as f])
  #+cljs (:require [cljs-time.format :as f])
  #+cljs (:require-macros [bouncer.validators :refer [defvalidator]]))

;; ## Customization support
;;
;; The following functions and macros support creating custom validators

(defmacro defvalidator
  "Defines a new validating function using args & body semantics as provided by \"defn\".
  docstring and opts-map are optional

  opts-map is a map of key-value pairs and may be one of:

  - `:default-message-format` used when the client of this validator doesn't
  provide a message (consider using custom message functions)

  - `:optional` whether the validation should be run only if the given key has
  a non-nil value in the map. Defaults to false.

  or any other key-value pair which will be available in the validation result
  under the `:metadata` key.

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
  (let [[docstring options] (if (string? (first options))
                                 [(first options) (next options)]
                                 [nil options])
        [fn-meta [args & body]] (if (map? (first options))
                                  [(first options) (next options)]
                                  [nil options])
        fn-meta (assoc fn-meta
                       :validator (keyword (str *ns*) (str name)))]
    (let [arglists ''([name])]
      `(do (def ~name (with-meta (fn ~name
                                   ([~@args]
                                    ~@body)) ~fn-meta))))))

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

(defvalidator integer
  "Validates maybe-an-int is a valid integer.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be an integer"}
  [maybe-an-int]
  (integer? maybe-an-int))

(defvalidator boolean
  "Validates maybe-a-boolean is a valid boolean.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be a boolean"}
  [maybe-a-boolean]
  (or (= false maybe-a-boolean)
      (= true maybe-a-boolean)))

(defvalidator string
  "Validates maybe-a-string is a valid string.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be a string"}
  [maybe-a-string]
  (string? maybe-a-string))

(defvalidator in-range
  "Validates number is inside specified range [from to].

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be in a specified range"}
  [value [from to]]
  (<= from value to))

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

(defvalidator datetime
  "Validates value is a date(time). 

  Optionally, takes a formatter argument which may be either an existing clj-time formatter, or a string representing a custom datetime formatter.

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s must be a valid date"}
  [value & [opt & _]]
  (let [formatter (if (string? opt) (f/formatter opt) opt)]
    (try
      (if formatter (f/parse formatter value) (f/parse value))
      #+clj (catch IllegalArgumentException e false)
      #+cljs (catch js/Error e false))))

(defvalidator max-count
  "Validates value is not greater than a max count

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s is longer than the maximum"}
  [value maximum]
  (<= (count (seq value)) maximum))

(defvalidator min-count
  "Validates value at least meets the minimum count

  For use with validation functions such as `validate` or `valid?`"
  {:default-message-format "%s is less than the minimum"}
  [value minimum]
  (>= (count (seq value)) minimum))

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
  "Returns a validation function that checks k is present in the map being validated.
If no message is given, a default message will be used"
  ([k]
     (required k (format "%s must be present" (key->name k))))
  ([k msg]
     (mk-validator #(if (string? %)
                      (not (empty? %)) 
                      (not (nil? %))) k msg)))

(defn number
  "Returns a validation function that checks the value for k is a number.
If no message is given, a default message will be used"
  ([k]
     (number k (format "%s must be a number" (key->name k))))
  ([k msg]
     (mk-validator number? k msg :optional true)))

(defn positive
  "Returns a validation function that checks the value for k is greater than zero.
If no message is given, a default message will be used"
  ([k]
     (positive k (format "%s must be a positive number" (key->name k))))
  ([k msg]
     (mk-validator #(> (or % 0) 0) k msg :optional true)))


(defmacro validate
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
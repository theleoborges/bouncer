(ns bouncer.core
  "The `core` namespace provides the two main entry point functions in bouncer:

  - `validate`
  - `valid?`


All other functions are meant for internal use only and shouldn't be relied upon.

The project [README](https://github.com/leonardoborges/bouncer/blob/master/README.md) should get you started,
it's pretty comprehensive.


If you'd like to know more about the motivation behind `bouncer`, check the
[announcement post](http://www.leonardoborges.com/writings/2013/01/04/bouncer-validation-lib-for-clojure/)."
  {:author "Leonardo Borges"}
  (:require [clojure.algo.monads :as m]))



;; ## Internal utility functions

(defn- build-multi-step
  ([key-or-vec fn-vec] (build-multi-step key-or-vec fn-vec []))
  ([key-or-vec [f-or-list & rest] acc]
     (if-not f-or-list
       acc
       (cond
        (sequential? f-or-list)
        (let [[f & opts] f-or-list]
          (recur key-or-vec
                 rest
                 (conj acc (concat [f key-or-vec ] opts))))

        :else (recur key-or-vec
                     rest
                     (conj acc [f-or-list key-or-vec]))))))

(defn- merge-path
  "Takes two arguments:

  `parent-keyword` is a :keyword - or a vector of :keywords denoting a path in a associative structure

  `validations-map` is a map of forms following this spec:


      {:keyword [f g] :another-keyword h}


  Merges `:parent-keyword` with every first element of validations-map, transforming it into:


      ([:parent-keyword :keyword] [f g] [:parent-keyword :another-keyword] h)
"
  [parent-key validations-map]
  (let [parent-key (if (keyword? parent-key) [parent-key] parent-key)]
    (mapcat (fn [[key validations]]
              (if (vector? key)
                [(apply vector (concat parent-key key)) validations]
                [(apply vector (concat parent-key [key])) validations]))
            validations-map)))

(defn- build-steps [[head & tail :as forms]]
  (let [forms (if (map? head)
                (vec (mapcat identity head))
                forms)]
    (reduce (fn [acc [key-or-vec sym-or-coll :as rule]]
              (cond
               (vector? sym-or-coll)
               (concat acc (build-multi-step key-or-vec sym-or-coll))


               (map? sym-or-coll)
               (concat acc (build-steps (merge-path key-or-vec
                                                    sym-or-coll)))

               :else (conj acc [sym-or-coll key-or-vec])))
            []
            (partition 2 forms))))

(defn- pre-condition-met? [pre-fn map]
  (or (nil? pre-fn) (pre-fn map)))

(defn- wrap
  "Wraps pred in the context of validating a single value

  - `acc`  is the map being validated

  - `pred` is a validator

  - `k`    the path to the value to be validated in the associative structure `acc`

  - `args` any extra args to pred

  It only runs pred if:

  - the validator contains a pre-condition *and* it is met or;
  - the validator is optional  *and* there is a non-nil value to be validated (this information is read from pred's metadata) or;
  - there are no previous errors for the given path

  Returns `acc` augmented with a namespace qualified ::errors keyword
"
  [message-fn acc [pred k & args]]
  (let [k (if (vector? k) k [k])
        error-path (cons ::errors k)

        {:keys [optional default-message-format]
         :or {optional false
              default-message-format "Custom validation failed for %s"}
         :as metadata} (meta pred)

        meta-with-defaults
        (merge metadata {:default-message-format default-message-format
                         :optional optional})

        [args opts] (split-with (complement keyword?) args)
        {:keys [message pre]} (apply hash-map opts)
        pred-subject (get-in acc k)]
    (if (pre-condition-met? pre acc)
      (if (or (and optional (nil? pred-subject))
              (not (empty? (get-in acc error-path)))
              (apply pred pred-subject args))
        acc
        (update-in acc error-path
                   #(conj % (message-fn {:path k, :value pred-subject
                                         :args (seq args)
                                         :metadata meta-with-defaults
                                         :message message}))))
      acc)))


(defn- wrap-chain
  "Internal Use.

  Chain of responsibility.

  Takes a collection of validators `fs` and returns a `state monad`-compatible function.

  This function will run all validators against `old-state` and eventually return a vector with the result - the errors map - and the new state - the original map augmented with the errors map.

  See also `wrap`
"
  [message-fn & fs]
  (fn [old-state]
    (let [new-state (reduce (partial wrap message-fn)
                            old-state
                            fs)]
      [(::errors new-state) new-state])))

(defn- validate*
  "Internal use.

  Validates the map m using the validation functions fs.

  Returns a vector where the first element is the map of validation errors if any and the second is the original map (possibly)augmented with the errors map."
  [message-fn m fs]
  (letfn [(m-fn [fs]
            (m/with-monad m/state-m
              (cond
                (> (count fs) 1) (m-bind
                                   (bouncer.core/wrap-chain message-fn (first fs))
                                   (fn [_]
                                     (m-fn (rest fs))))
                :else (m-bind (bouncer.core/wrap-chain message-fn (first fs))
                              (fn [result]
                                (m-result result))))))]
    ((m-fn fs) m)))

;; ## Public API

(defn with-default-messages
  "Use together with `validate`, e.g.:


      (core/validate core/with-default-messages {}
                     :name v/required)"
  [error]
  (let [{:keys [message path metadata]} error]
    (format (or message (:default-message-format metadata))
            (name (peek path)))))

(defn validate
  "Takes a

  - `message-fn` (optional) responsible for transforming error metadata into
  the validation result (e.g. `with-default-messages`)

  - `m` map to be validated

  - `forms` validations to be performed on the map

  forms can be a single validator set or a sequence of key/value pairs where:

  key   ==> :keyword or [:a :path]

  value ==> validation-function or
            validator-set or
           [[validation-function args+opts]] or
           [validation-function another-validation-function] or
           [validation-function [another-validation-function args+opts]]

  e.g.:


      (core/validate a-map
               :name v/required
               :age  [v/required
                     [v/number :message \"age must be a number\"]]
               [:passport :number] v/positive)


  Returns a vector where the first element is the map of validation errors if
  any and the second is the original map (possibly) augmented with the errors
  map.

  See also `defvalidator`
"
  [& args]
  (let [[message-fn args] (if (fn? (first args))
                                   [(first args) (next args)]
                                   [identity args])
        [m forms] [(first args) (next args)]]
    (validate* message-fn m (build-steps forms))))

(defn valid?
  "Takes a map and one or more validation functions with semantics provided by \"validate\". Returns true if the map passes all validations. False otherwise."
  [& args]
  (empty? (first (apply validate args))))

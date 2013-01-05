(ns bouncer.core
  (:require [clojure.algo.monads :as m]
            [bouncer.helpers :as h]))



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
                 (conj acc `(~(h/resolve-or-same f) ~key-or-vec ~@opts))))

        :else (recur key-or-vec
                     rest
                     (conj acc `(~(h/resolve-or-same f-or-list) ~key-or-vec)))))))

(def is-validator-set? (comp true?
                             :bouncer-validator-set
                             meta
                             h/resolve-or-same))

(defn merge-path
  "Takes two arguments:

  - parent-keyword is a a keyword - or a vector of :keywords denoting a path in a associative structire
  - coll is a seq of forms following following this spec:

    (:keyword [f g] :another-keyword h)

  Merges :parent-keyword every first element of coll, transforming coll into:

    ([:parent-keyword :keyword] [f g] [:parent-keyword :another-keyword] h)
"
  [parent-key coll]
  (let [pairs (partition 2 coll)]
    (mapcat #(if (vector? (first %))
               [(apply vector parent-key (first %)) (second %)]
               [(vector parent-key (first %)) (second %)])
            pairs)))

(defn build-steps [forms]
  (reduce (fn [acc [key-or-vec sym-or-coll :as rule]]
            (cond
             (vector? sym-or-coll)
             (concat acc (build-multi-step key-or-vec sym-or-coll))
             
             (list? sym-or-coll)
             (concat acc (build-multi-step key-or-vec [sym-or-coll]))
             
             (is-validator-set? sym-or-coll)
             (concat acc (build-steps (merge-path key-or-vec
                                                  (var-get (h/resolve-or-same sym-or-coll)))))

             :else (conj acc `(~(h/resolve-or-same sym-or-coll) ~key-or-vec))))
          []
          (partition 2 forms)))

(defmacro validate*
  "Internal User. Validates the map m using the validation functions fs.
Returns a vector where the first element is the map of validation errors if any and the second is the original map (possibly)augmented with the errors map."
  [m & fs]
  (let [ignore (gensym "ignore__")
        result (gensym "result__")
        fns-pairs (vec (interleave (repeat (count fs) ignore) fs))]
    `((m/domonad m/state-m
                 ~(assoc fns-pairs (- (count fns-pairs) 2) result)
                 ~result) ~m)))

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

(defmacro valid? [& args]
  "Takes a map and one or more validation functions. Returns true if the map passes all validations. False otherwise"
  `(empty? (first (validate ~@args))))
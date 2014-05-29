(ns bouncer.utils)

(defn bouncify [f]
  (fn [& args]
    (apply f (butlast args))))

(ns bouncer.helpers)

(defn resolve-or-same [form]
  (if (and (symbol? form)
           (resolve form))
    (resolve form)
    form))
(ns bouncer.helpers
  {:author "Leonardo Borges"})

(defn resolve-or-same [form]
  (if (and (symbol? form)
           (resolve form))
    (resolve form)
    form))
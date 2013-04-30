(ns bouncer.helpers
  {:author "Leonardo Borges"})

(defn resolve-or-same [form]
  (if (and (symbol? form)
           (resolve form))
    (resolve form)
    form))

(defn get-var-or-same [form]
  (if (var? form)
    (var-get form)
    form))
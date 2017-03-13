(ns bouncer.runner
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [bouncer.core-test]
    [bouncer.validators-test]))

(doo-tests
  'bouncer.core-test
  'bouncer.validators-test)

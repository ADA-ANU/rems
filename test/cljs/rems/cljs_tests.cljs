(ns rems.cljs-tests
  (:require [doo.runner :refer-macros [doo-tests]]
            rems.administration.test-create-catalogue-item
            rems.administration.test-create-form
            rems.administration.test-create-license
            rems.administration.test-create-resource
            rems.administration.test-create-workflow
            rems.administration.test-items
            rems.test-application
            rems.test-util))

(doo-tests 'rems.administration.test-create-catalogue-item
           'rems.administration.test-create-form
           'rems.administration.test-create-license
           'rems.administration.test-create-resource
           'rems.administration.test-create-workflow
           'rems.administration.test-items
           'rems.test-application
           'rems.test-util)

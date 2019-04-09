(ns ^:integration rems.api.test-public
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.handler :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest service-translations-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/translations")
                   (authenticate api-key user-id)
                   handler
                   read-body)
          languages (keys data)]
      (is (= [:en :fi] (sort languages))))))

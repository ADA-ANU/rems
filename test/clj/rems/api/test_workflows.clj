(ns ^:integration rems.api.test-workflows
  (:require [clojure.test :refer :all]
            [rems.common-util :refer [index-by]]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest workflows-api-test
  (testing "list"
    (let [data (-> (request :get "/api/workflows")
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          wfs (index-by [:title] data)
          simple (get wfs "simple")]
      (is (coll-is-not-empty? data))
      (is simple)
      (is (= 0 (:final-round simple)))
      (is (= [{:actoruserid "developer"
               :round 0
               :role "approver"}
              {:actoruserid "bob"
               :round 0
               :role "approver"}]
             (:actors simple)))))

  (testing "create auto-approved workflow"
    (let [body (-> (request :post (str "/api/workflows/create"))
                   (json-body {:organization "abc"
                               :title "auto-approved workflow"
                               :type :auto-approve})
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          id (:id body)]
      (is (< 0 id))
      (testing "and fetch"
        (let [workflows (-> (request :get "/api/workflows")
                            (authenticate "42" "owner")
                            handler
                            assert-response-is-ok
                            read-body)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (= {:id id
                  :organization "abc"
                  :title "auto-approved workflow"
                  :final-round 0
                  :actors []}
                 (select-keys workflow [:id :organization :title :final-round :actors])))))))

  (testing "create dynamic workflow"
    (let [body (-> (request :post "/api/workflows/create")
                   (json-body {:organization "abc"
                               :title "dynamic workflow"
                               :type :dynamic
                               :handlers ["bob" "carl"]})
                   (authenticate "42" "owner")
                   handler
                   assert-response-is-ok
                   read-body)
          id (:id body)]
      (is (< 0 id))
      (testing "and fetch"
        (let [workflows (-> (request :get "/api/workflows")
                            (authenticate "42" "owner")
                            handler
                            assert-response-is-ok
                            read-body)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (= {:id id
                  :organization "abc"
                  :title "dynamic workflow"
                  :workflow {:type "workflow/dynamic"
                             :handlers ["bob" "carl"]}
                  :enabled true
                  :archived false}
                 (select-keys workflow [:id :organization :title :workflow :enabled :archived])))))
      (testing "and update"
        (-> (request :put "/api/workflows/update")
            (json-body {:id id :enabled false :archived true})
            (authenticate "42" "owner")
            handler
            assert-response-is-ok)
        (let [workflows (-> (request :get "/api/workflows" {:disabled true :archived true})
                            (authenticate "42" "owner")
                            handler
                            assert-response-is-ok
                            read-body)
              workflow (first (filter #(= id (:id %)) workflows))]
          (is (= {:id id :enabled false :archived true}
                 (select-keys workflow [:id :enabled :archived]))))))))

(deftest workflows-update-test
  (let [api-key "42"
        user-id "owner"
        wf-spec {:organization "abc"
                 :title "dynamic workflow"
                 :type :dynamic
                 :handlers ["bob" "carl"]}
        wfid (-> (request :post "/api/workflows/create")
                 (json-body wf-spec)
                 (authenticate api-key user-id)
                 handler
                 read-ok-body
                 :id)
        ;; this is a subset of what we expect to get from the api
        expected {:id wfid
                  :organization "abc"
                  :title "dynamic workflow"
                  :workflow {:type "workflow/dynamic"
                             :handlers ["bob" "carl"]}
                  :enabled true
                  :expired false
                  :archived false}
        fetch (fn []
                (let [wfs (-> (request :get "/api/workflows" {:archived true :expired true :disabled true})
                              (authenticate api-key user-id)
                              handler
                              read-ok-body)]
                  (select-keys
                   (first (filter #(= wfid (:id %)) wfs))
                   (keys expected))))
        update #(-> (request :put "/api/workflows/update")
                    (json-body (merge {:id wfid} %))
                    (authenticate api-key user-id)
                    handler
                    read-ok-body)]
    (testing "before changes"
      (is (= expected (fetch))))
    (testing "disable and archive"
      (is (:success (update {:enabled false :archived true})))
      (is (= (assoc expected
                    :enabled false
                    :archived true)
             (fetch))))
    (testing "re-enable"
      (is (:success (update {:enabled true})))
      (is (= (assoc expected
                    :archived true)
             (fetch))))
    (testing "change title"
      (is (:success (update {:title "x"})))
      (is (= (assoc expected
                    :archived true
                    :title "x")
             (fetch))))
    (testing "change handlers"
      (is (:success (update {:handlers ["owner" "alice"]})))
      (is (= (assoc expected
                    :archived true
                    :title "x"
                    :workflow {:type "workflow/dynamic"
                               :handlers ["owner" "alice"]})
             (fetch))))))

(deftest workflows-api-filtering-test
  (let [unfiltered (-> (request :get "/api/workflows" {:expired true})
                       (authenticate "42" "owner")
                       handler
                       assert-response-is-ok
                       read-body)
        filtered (-> (request :get "/api/workflows")
                     (authenticate "42" "owner")
                     handler
                     assert-response-is-ok
                     read-body)]
    (is (coll-is-not-empty? unfiltered))
    (is (coll-is-not-empty? filtered))
    (is (every? #(contains? % :expired) unfiltered))
    (is (not-any? :expired filtered))
    (is (< (count filtered) (count unfiltered)))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["bob"]}]})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization "abc"
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["bob"]}]})
                         (authenticate "42" "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))

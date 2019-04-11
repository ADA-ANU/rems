(ns ^:integration rems.db.test-entitlements
  (:require [cheshire.core :as cheshire]
            [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.entitlements :as entitlements]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.poller.entitlements :as entitlements-poller]
            [rems.testing-util :refer [suppress-logging]]
            [stub-http.core :as stub]))

(use-fixtures
  :once
  (suppress-logging "rems.db.entitlements")
  test-db-fixture
  rollback-db-fixture
  test-data-fixture)

(def +entitlements+
  [{:resid "res1" :catappid 11 :userid "user1" :start (time/date-time 2001 10 11) :mail "user1@tes.t"}
   {:resid "res2" :catappid 12 :userid "user2" :start (time/date-time 2002 10 11) :mail "user2@tes.t"}])

(def +expected-payload+
  [{"resource" "res1" "application" 11 "user" "user1" "mail" "user1@tes.t"}
   {"resource" "res2" "application" 12 "user" "user2" "mail" "user2@tes.t"}])

(defn run-with-server
  "Run callback with a mock entitlements http server set up.
   Return sequence of data received by mock server."
  [endpoint-spec callback]
  (with-open [server (stub/start! {"/entitlements" endpoint-spec})]
    (with-redefs [rems.config/env {:entitlements-target
                                   {:add (str (:uri server) "/entitlements")}}]
      (callback)
      (for [r (stub/recorded-requests server)]
        (cheshire/parse-string (get-in r [:body "postData"]))))))

(deftest test-post-entitlements
  (let [log (atom [])]
    (with-redefs [db/log-entitlement-post! #(swap! log conj %)]
      (testing "ok"
        (is (= [+expected-payload+]
               (run-with-server {:status 200}
                                #(#'entitlements/post-entitlements :add +entitlements+))))
        (let [[{payload :payload status :status}] @log]
          (is (= 200 status))
          (is (= +expected-payload+ (cheshire/parse-string payload))))
        (reset! log []))
      (testing "not found"
        (run-with-server {:status 404}
                         #(#'entitlements/post-entitlements :add +entitlements+))
        (let [[{payload :payload status :status}] @log]
          (is (= 404 status))
          (is (= +expected-payload+ (cheshire/parse-string payload))))
        (reset! log []))
      (testing "timeout"
        (run-with-server {:status 200 :delay 5000} ;; timeout of 2500 in code
                         #(#'entitlements/post-entitlements :add +entitlements+))
        (let [[{payload :payload status :status}] @log]
          (is (= "exception" status))
          (is (= +expected-payload+ (cheshire/parse-string payload)))))
      (testing "no server"
        (with-redefs [rems.config/env {:entitlements-target "http://invalid/entitlements"}]
          (#'entitlements/post-entitlements :add +entitlements+)
          (let [[{payload :payload status :status}] @log]
            (is (= "exception" status))
            (is (= +expected-payload+ (cheshire/parse-string payload)))))))))

(deftest test-entitlement-granting
  (entitlements-poller/run) ; clear previous entitlements
  (with-open [server (stub/start! {"/add" {:status 200}
                                   "/remove" {:status 200}})]
    (with-redefs [rems.config/env {:entitlements-target
                                   {:add (str (:uri server) "/add")
                                    :remove (str (:uri server) "/remove")}}]
      (let [uid "bob"
            memberid "elsa"
            admin "owner"
            organization "foo"
            workflow {:type :workflow/dynamic :handlers [admin]}
            wfid (:id (db/create-workflow! {:organization "abc" :modifieruserid "owner" :owneruserid "owner" :title "dynamic" :fnlround -1 :workflow (cheshire/generate-string workflow)}))
            formid (:id (db/create-form! {:organization "abc" :title "internal-title" :user "owner"}))
            lic-id1 (:id (licenses/create-license! {:licensetype "text"
                                                    :title "license1"
                                                    :textcontent "license1 text"
                                                    :localizations {}}
                                                   "owner"))
            lic-id2 (:id (licenses/create-license! {:licensetype "text"
                                                    :title "license2"
                                                    :textcontent "license2 text"
                                                    :localizations {}}
                                                   "owner"))
            res1 (:id (resource/create-resource! {:resid "resource1" :organization organization :licenses [lic-id1]} "owner"))
            res2 (:id (resource/create-resource! {:resid "resource2" :organization organization :licenses [lic-id2]} "owner"))
            item1 (:id (db/create-catalogue-item! {:title "item1" :form formid :resid res1 :wfid wfid}))
            item2 (:id (db/create-catalogue-item! {:title "item2" :form formid :resid res2 :wfid wfid}))]
        (db/add-user! {:user uid :userattrs (cheshire/generate-string {"mail" "b@o.b"})})
        (db/add-user! {:user memberid :userattrs (cheshire/generate-string {"mail" "e.l@s.a"})})
        (db/add-user! {:user admin :userattrs nil})
        (let [app-id (:application-id (applications/create-application! uid [item1 item2]))]
          (is (nil? (applications/command! {:type :application.command/submit
                                            :actor uid
                                            :application-id app-id
                                            :time (time/now)})))
          (is (nil? (applications/command! {:type :application.command/add-member
                                            :actor admin
                                            :application-id app-id
                                            :member {:userid memberid}
                                            :time (time/now)})))
          (testing "submitted application should not yet cause entitlements"
            (entitlements-poller/run)
            (is (empty? (db/get-entitlements {:application app-id})))
            (is (empty? (stub/recorded-requests server))))

          (is (nil? (applications/command! {:type :application.command/approve
                                            :actor admin
                                            :application-id app-id
                                            :comment ""
                                            :time (time/now)})))

          (testing "approved application should not yet cause entitlements"
            (entitlements-poller/run)
            (is (empty? (db/get-entitlements {:application app-id})))
            (is (empty? (stub/recorded-requests server))))

          (is (nil? (applications/command! {:type :application.command/accept-licenses
                                            :actor uid
                                            :application-id app-id
                                            :accepted-licenses [lic-id1 lic-id2]
                                            :time (time/now)})))

          (is (nil? (applications/command! {:type :application.command/accept-licenses
                                            :actor memberid
                                            :application-id app-id
                                            :accepted-licenses [lic-id1] ; only accept some licenses
                                            :time (time/now)})))

          (is (= {uid #{lic-id1 lic-id2}
                  memberid #{lic-id1}}
                 (:application/accepted-licenses (applications/get-application-state app-id))))

          (testing "approved application, licenses accepted by one user generates entitlements for that user"
            (entitlements-poller/run)
            (entitlements-poller/run) ;; run twice to check idempotence
            (is (= 2 (count (stub/recorded-requests server))))
            (testing "db"
              (is (= [[uid "resource1"] [uid "resource2"]]
                     (map (juxt :userid :resid) (db/get-entitlements {:application app-id})))))
            (testing "POST"
              (let [data (take 2 (stub/recorded-requests server))
                    targets (map :path data)
                    bodies (->> data
                                (map #(get-in % [:body "postData"]))
                                (map #(cheshire/parse-string % keyword))
                                (apply concat))]
                (is (every? #{"/add"} targets))
                (is (= [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b"}
                        {:resource "resource2" :application app-id :user "bob" :mail "b@o.b"}]
                       bodies)))))

          (is (nil? (applications/command! {:type :application.command/accept-licenses
                                            :actor memberid
                                            :application-id app-id
                                            :accepted-licenses [lic-id1 lic-id2] ; now accept all licenses
                                            :time (time/now)})))

          (testing "approved application, more accepted licenses generates more entitlements"
            (entitlements-poller/run)
            (is (= 4 (count (stub/recorded-requests server))))
            (testing "db"
              (is (= [[uid "resource1"] [uid "resource2"]
                      [memberid "resource1"] [memberid "resource2"]]
                     (map (juxt :userid :resid) (db/get-entitlements {:application app-id})))))
            (testing "POST"
              (let [data (take 2 (drop 2 (stub/recorded-requests server)))
                    targets (map :path data)
                    bodies (->> data
                                (map #(get-in % [:body "postData"]))
                                (map #(cheshire/parse-string % keyword))
                                (apply concat))]
                (is (every? #{"/add"} targets))
                (is (= [{:resource "resource1" :application app-id :user "elsa" :mail "e.l@s.a"}
                        {:resource "resource2" :application app-id :user "elsa" :mail "e.l@s.a"}]
                       bodies)))))

          (is (nil? (applications/command! {:type :application.command/remove-member
                                            :actor admin
                                            :application-id app-id
                                            :member {:userid memberid}
                                            :comment "Left team"
                                            :time (time/now)})))

          (testing "removing a member ends entitlements"
            (entitlements-poller/run)
            (is (= 5 (count (stub/recorded-requests server))))
            (testing "db"
              (is (= [[uid "resource1"] [uid "resource2"]]
                     (map (juxt :userid :resid) (db/get-entitlements {:application app-id :is-active? true})))))
            (testing "POST"
              (let [data (nth (stub/recorded-requests server) 4)
                    target (:path data)
                    body (cheshire/parse-string (get-in data [:body "postData"]) keyword)]
                (is (= "/remove" target))
                (is (= [{:resource "resource1" :application app-id :user "elsa" :mail "e.l@s.a"}
                        {:resource "resource2" :application app-id :user "elsa" :mail "e.l@s.a"}]
                       body)))))

          (is (nil? (applications/command! {:type :application.command/close
                                            :actor admin
                                            :application-id app-id
                                            :comment ""
                                            :time (time/now)})))

          (testing "closed application should end entitlements"
            (entitlements-poller/run)
            (is (= 6 (count (stub/recorded-requests server))))
            (testing "db"
              (is (= []
                     (map (juxt :userid :resid) (db/get-entitlements {:application app-id :is-active? true})))))
            (testing "POST"
              (let [data (nth (stub/recorded-requests server) 5)
                    target (:path data)
                    body (cheshire/parse-string (get-in data [:body "postData"]) keyword)]
                (is (= "/remove" target))
                (is (= [{:resource "resource1" :application app-id :user "bob" :mail "b@o.b"}
                        {:resource "resource2" :application app-id :user "bob" :mail "b@o.b"}]
                       body))))))))))

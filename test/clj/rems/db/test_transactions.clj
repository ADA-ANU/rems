(ns ^:integration rems.db.test-transactions
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [conman.core :as conman]
            [rems.db.applications :as applications]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.resource :as resource]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.workflow.dynamic :as dynamic])
  (:import [java.sql SQLException]
           [java.util UUID]
           [java.util.concurrent Executors Future TimeUnit ExecutorService]
           [org.postgresql.util PSQLException]))

(use-fixtures
  :once
  test-db-fixture)

(defn- create-dummy-user []
  (let [user-id "user"]
    (users/add-user! user-id {:eppn user-id})
    user-id))

(defn- create-dummy-application [user-id]
  (let [workflow-id (:id (workflow/create-workflow! {:user-id user-id
                                                     :organization ""
                                                     :title ""
                                                     :type :dynamic
                                                     :handlers []}))
        form-id (:id (form/create-form! user-id
                                        {:organization ""
                                         :title ""
                                         :fields []}))
        res-id (:id (resource/create-resource! {:resid (str "urn:uuid:" (UUID/randomUUID))
                                                :organization ""
                                                :licenses []}
                                               user-id))
        cat-id (:id (catalogue/create-catalogue-item! {:title ""
                                                       :form form-id
                                                       :resid res-id
                                                       :wfid workflow-id}))
        app-id (:application-id (applications/create-application! user-id [cat-id]))]
    (assert app-id)
    app-id))

(defn- transaction-conflict? [^Exception e]
  (cond
    (nil? e) false
    (instance? PSQLException e) (.contains (.getMessage e)
                                           "The transaction might succeed if retried")
    :else (transaction-conflict? (.getCause e))))

(defn- sample-until-interrupted [f]
  (loop [results []]
    (if (.isInterrupted (Thread/currentThread))
      results
      (recur (try
               (conj results (f))
               (catch InterruptedException _
                 (.interrupt (Thread/currentThread))
                 results)
               ;; XXX: HikariPool.getConnection wraps InterruptedException into SQLException
               (catch SQLException e
                 (if (instance? InterruptedException (.getCause e))
                   (do (.interrupt (Thread/currentThread))
                       results)
                   (throw e))))))))

(defn- submit-all [^ExecutorService thread-pool tasks]
  (doall (for [task tasks]
           (.submit thread-pool ^Callable task))))

(deftest test-event-publishing-consistency
  (let [test-duration-millis 2000
        applications-count 5
        writers-per-application 5
        concurrent-readers 5
        user-id (create-dummy-user)
        app-ids (vec (for [_ (range applications-count)]
                       (create-dummy-application user-id)))
        observed-app-version-marker 999
        mark-observed-app-version (fn [result _cmd application]
                                    (if (and (:success result)
                                             (= :application.event/draft-saved (:event/type (:result result))))
                                      (assoc-in result [:result :application/field-values observed-app-version-marker]
                                                (str (count (:application/events application))))
                                      result))
        write-event (fn [app-id]
                      (try
                        ;; Note: currently these tests pass only with serializable isolation
                        (conman/with-transaction [db/*db* {:isolation :serializable}]
                          (binding [dynamic/postprocess-command-result-for-tests mark-observed-app-version]
                            (applications/command!
                             {:type :application.command/save-draft
                              :time (time/now)
                              :actor user-id
                              :application-id app-id
                              :field-values []})))
                        (catch Exception e
                          (if (transaction-conflict? e)
                            ::transaction-conflict
                            (throw e)))))
        read-app-events (fn [app-id]
                          (conman/with-transaction [db/*db* {:isolation :read-committed
                                                             :read-only? true}]
                            {::app-id app-id
                             ::events (applications/get-application-events app-id)}))
        read-all-events (fn []
                          (conman/with-transaction [db/*db* {:isolation :read-committed
                                                             :read-only? true}]
                            {::events (applications/get-all-events-since 0)}))
        thread-pool (Executors/newCachedThreadPool)
        app-events-readers (submit-all thread-pool (for [app-id app-ids]
                                                     (fn [] (sample-until-interrupted
                                                             (fn [] (read-app-events app-id))))))
        all-events-readers (submit-all thread-pool (for [_ (range concurrent-readers)]
                                                     (fn [] (sample-until-interrupted
                                                             (fn [] (read-all-events))))))
        writers (submit-all thread-pool (for [app-id app-ids
                                              _ (range writers-per-application)]
                                          (fn [] (sample-until-interrupted
                                                  (fn [] (write-event app-id))))))]
    (Thread/sleep test-duration-millis)
    (doto thread-pool
      (.shutdownNow)
      (.awaitTermination 30 TimeUnit/SECONDS))

    (let [writer-results (->> (flatten (map #(.get ^Future %) writers))
                              (remove #{::transaction-conflict}))
          app-events-reader-results (flatten (map #(.get ^Future %) app-events-readers))
          all-events-reader-results (flatten (map #(.get ^Future %) all-events-readers))
          final-events (applications/get-all-events-since 0)
          final-events-by-app-id (group-by :application/id final-events)]

      (testing "all commands succeeded"
        (is (not (empty? writer-results)))
        (is (every? nil? writer-results))) ; successful commands return nil

      (testing "all events were written"
        (is (= (+ (count app-ids) ; one application created event per application
                  (count writer-results))
               (count final-events))))

      (testing "event IDs are observed in monotonically increasing order, within one application"
        (doseq [observed app-events-reader-results]
          (let [final-events (get final-events-by-app-id (::app-id observed))]
            (is (= (->> final-events
                        (take (count (::events observed)))
                        (map :event/id))
                   (->> (::events observed)
                        (map :event/id)))))))

      (testing "event IDs are observed in monotonically increasing order, globally"
        (doseq [observed all-events-reader-results]
          (is (= (->> final-events
                      (take (count (::events observed)))
                      (map :event/id))
                 (->> (::events observed)
                      (map :event/id))))))

      (testing "commands are executed serially"
        (doseq [[_ events] final-events-by-app-id]
          (is (= (->> (range 1 (count events))
                      (map str))
                 (->> events
                      (filter #(= :application.event/draft-saved (:event/type %)))
                      (map #(get-in % [:application/field-values observed-app-version-marker])))))))

      ;; TODO?
      #_(testing "there are not gaps in event IDs"
          (is (every? (fn [[a b]] (= (inc a) b))
                      (->> (map :event/id final-events)
                           (partition 2 1))))))))

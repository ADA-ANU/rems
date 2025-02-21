(ns rems.service.cadre.util
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.cadredb.projects]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.context :as context]
            [rems.util :refer [getx-user-id]]))

(defn- may-edit-project? [project]
  (let [owner? (contains? context/*roles* :owner)
        project-owners (set (map :userid (:project/owners project)))
        project-owner? (contains? project-owners (getx-user-id))]
    (or owner?
        project-owner?)))

(defn check-allowed-project! [project]
  (assert (:project/id project) {:error "invalid project"
                                 :project project})
  (when-not (may-edit-project? (rems.db.cadredb.projects/get-project-by-id-raw
                                (:project/id project)))
    (throw-forbidden (str "no access to project " (pr-str (:project/id project))))))

(deftest test-may-edit-project?
  (let [proj-empty {:project/id ""}
        proj-nobody {:project/id "project with no owners" :project/owners []}
        proj-bob {:project/id "project owned by bob" :project/owners [{:userid "bob"}]}
        proj-carl {:project/id "project owned by bob" :project/owners [{:userid "carl"}]}
        proj-bob-carl {:project/id "project owned by bob and carl" :project/owners [{:userid "bob"} {:userid "carl"}]}]
    (testing "for owner, all projects are permitted"
      (binding [context/*user* {:userid "owner"}
                context/*roles* #{:owner}]
        (is (may-edit-project? proj-empty))
        (is (may-edit-project? proj-nobody))
        (is (may-edit-project? proj-bob))
        (is (may-edit-project? proj-carl))
        (is (may-edit-project? proj-bob-carl))))

    (testing "for owner who is also an project owner, all projects are permitted"
      (binding [context/*user* {:userid "bob"}
                context/*roles* #{:owner}]
        (is (may-edit-project? proj-empty))
        (is (may-edit-project? proj-nobody))
        (is (may-edit-project? proj-bob))
        (is (may-edit-project? proj-carl))
        (is (may-edit-project? proj-bob-carl))))

    (testing "for project owner, only own projects are permitted"
      (binding [context/*user* {:userid "bob"}
                context/*roles* #{}]
        (is (not (may-edit-project? proj-empty)))
        (is (not (may-edit-project? proj-nobody)))
        (is (may-edit-project? proj-bob))
        (is (not (may-edit-project? proj-carl)))
        (is (may-edit-project? proj-bob-carl)))

      (testing ", even if they are a handler"
        (binding [context/*user* {:userid "bob"}
                  context/*roles* #{:handler}]
          (is (not (may-edit-project? proj-empty)))
          (is (not (may-edit-project? proj-nobody)))
          (is (may-edit-project? proj-bob))
          (is (not (may-edit-project? proj-carl)))
          (is (may-edit-project? proj-bob-carl)))))

    (testing "for other user, no projects are permitted"
      (binding [context/*user* {:userid "alice"}
                context/*roles* #{}]
        (is (not (may-edit-project? proj-empty)))
        (is (not (may-edit-project? proj-nobody)))
        (is (not (may-edit-project? proj-bob)))
        (is (not (may-edit-project? proj-carl)))
        (is (not (may-edit-project? proj-bob-carl)))))))

;; Hash-Map Identifier CO-Person Record
(defn map-type-to-identity [seq-of-hmaps]
  (reduce (fn [acc item]
            (assoc acc (:type item) (:identifier item)))
          {}
          seq-of-hmaps))
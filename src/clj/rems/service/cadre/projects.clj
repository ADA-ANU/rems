(ns rems.service.cadre.projects
  (:require [clojure.set :as set]
            [medley.core :refer [assoc-some find-first]]
            [rems.service.dependencies :as dependencies]
            [rems.service.cadre.util]
            [rems.auth.util]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.cadredb.projects :as projects]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

(defn- apply-user-permissions [userid projects]
  (let [user-roles (set/union (roles/get-roles userid)
                              (projects/get-all-project-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (some? (some #{:owner :project-owner :handler :reporter} user-roles))]
    (for [org projects]
      (if (or (nil? userid) can-see-all?)
        org
        (dissoc org
                :project/owners
                :enabled
                :archived)))))

(defn- owner-filter-match? [owner org]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:project/owners org))) owner)))

(defn- project-filters [userid owner projects]
  (->> projects
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (doall)))

(defn get-projects [& [{:keys [userid owner enabled archived]}]]
  (->> (projects/get-projects)
       (db/apply-filters (assoc-some {}
                                     :enabled enabled
                                     :archived archived))
       (project-filters userid owner)))

(defn get-project [userid org]
  (->> (get-projects {:userid userid})
       (find-first (comp #{(:project/id org)} :project/id))))

(defn add-project! [cmd]
  (if-let [id (projects/add-project! cmd)]
    {:success true
     :project/id id}
    {:success false
     :errors [{:type :t.actions.errors/duplicate-id
               :project/id (:project/id cmd)}]}))

(defn edit-project! [cmd]
  (let [id (:project/id cmd)]
    (rems.service.cadre.util/check-allowed-project! cmd)
    (projects/update-project! id (fn [project] (->> (dissoc cmd :project/id)
                                                                   (merge project))))
    {:success true
     :project/id id}))

(defn set-project-enabled! [{:keys [enabled] :as cmd}]
  (let [id (:project/id cmd)]
    (projects/update-project! id (fn [project] (assoc project :enabled enabled)))
    {:success true}))

(defn set-project-archived! [{:keys [archived] :as cmd}]
  (let [id (:project/id cmd)]
    (or (dependencies/change-archive-status-error archived  {:project/id id})
        (do
          (projects/update-project! id (fn [project] (assoc project :archived archived)))
          {:success true}))))

(defn get-available-owners [] (users/get-users))

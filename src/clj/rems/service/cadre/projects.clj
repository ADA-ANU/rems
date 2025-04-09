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
                              (applications/get-all-application-roles userid))
        can-see-all? (some? (some #{:owner :handler :reporter} user-roles))]
    (filter #(or (nil? userid) can-see-all? (rems.service.cadre.util/may-view-projects? userid %)) projects)))

(defn- owner-filter-match? [owner proj]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:project/owners proj))) owner)))

(defn- collaborator-filter-match? [collaborator proj]
  (or (nil? collaborator) ; return all when not specified
      (contains? (set (map :userid (:project/collaborators proj))) collaborator)))

(defn- project-filters [userid owner collaborator projects]
  (->> projects
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (filter (partial collaborator-filter-match? collaborator))
       (doall)))

(defn get-projects [& [{:keys [userid owner collaborator enabled archived]}]]
  (->> (projects/get-projects)
       (db/apply-filters (assoc-some {}
                                     :enabled enabled
                                     :archived archived))
       (project-filters userid owner collaborator)))

(defn get-project [userid proj]
  (->> (get-projects {:userid userid})
       (find-first (comp #{(:project/id proj)} :project/id))))

(defn add-project! [userid proj]
  (if-let [id (projects/add-project! userid proj)]
    {:success true
     :project/id id}
    {:success false
     :errors [{:type :t.actions.errors/duplicate-id
               :project/id (:project/id proj)}]}))

(defn edit-project! [cmd]
  (let [id (:project/id cmd)]
    (rems.service.cadre.util/check-allowed-project! cmd)
    (projects/update-project! id (fn [project] (->> (dissoc cmd :project/id)
                                                    (merge project))))
    {:success true
     :project/id id}))

(defn link-project! [cmd]
  (let [id (:project/id cmd)]
    (rems.service.cadre.util/check-allowed-project! cmd)
    (if-let [apid (projects/link-project! (:application/id cmd) id)]
      {:success true
       :project-application/id apid}
      {:success false})))

(defn set-project-enabled! [{:keys [enabled] :as cmd}]
  (let [id (:project/id cmd)]
    (rems.service.cadre.util/check-allowed-project! cmd)
    (projects/update-project! id (fn [project] (assoc project :enabled enabled)))
    {:success true}))

(defn set-project-archived! [{:keys [archived] :as cmd}]
  (let [id (:project/id cmd)]
    (rems.service.cadre.util/check-allowed-project! cmd)
    (or (dependencies/change-archive-status-error archived  {:project/id id})
        (do
          (projects/update-project! id (fn [project] (assoc project :archived archived)))
          {:success true}))))

(defn get-available-owners [] (users/get-users))

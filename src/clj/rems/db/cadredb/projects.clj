(ns rems.db.cadredb.projects
  (:require [medley.core :refer [update-existing]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.users :as users]
            [rems.db.applications :as applications]
            [rems.schema-base-cadre :as schema-base-cadre]
            [rems.schema-base :as schema-base]
            [schema.core :as s]
            [schema.coerce :as coerce])
  (:import rems.DataException)
  (:import (org.joda.time DateTime)))

(s/defschema ApplicationIds
  {:id s/Int});

(s/defschema ProjectRaw
  (merge schema-base-cadre/ProjectOverview
         {(s/optional-key :project/owners) [schema-base/User]
          (s/optional-key :project/collaborators) [schema-base/User]
          (s/optional-key :project/applications) [ApplicationIds]
          (s/optional-key :project/end-date) DateTime
          (s/optional-key :project/RAiD) s/Str
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

(def ^:private validate-project
  (s/validator ProjectRaw))

(defn link-project! [appid projectid]
  (:id (db/link-project-application! {:appid appid
                                      :projectid projectid})))

(defn add-project! [userid proj]
  (validate-project proj)
  (:id (db/add-project! {:userid userid
                         :data (json/generate-string (-> proj
                                                         (assoc :enabled true
                                                                :archived false)
                                                         (dissoc :project/id)))})))

(def ^:private coerce-project-raw
  (coerce/coercer! ProjectRaw json/coercion-matcher))

(def ^:private coerce-project-full
  (coerce/coercer! schema-base-cadre/ProjectFull json/coercion-matcher))

(defn- parse-project [raw]
  (merge
   (json/parse-string (:data raw))
   {:project/id (:id raw)}))

(defn- parse-project-application [raw]
  (merge
   raw
   {:project/applications (db/get-applications-for-project! {:id (:project/id raw)})}))

(defn get-application-projects [application-id]
  (db/get-application-projects {:application application-id}))

(defn get-projects-raw []
  (->> (db/get-projects)
       (mapv parse-project)
       (mapv parse-project-application)
       (mapv coerce-project-raw)))

(defn get-project-by-id-raw [id]
  (when-some [project (db/get-project-by-id {:id id})]
    (-> project
        (parse-project)
        (parse-project-application)
        (coerce-project-raw))))

(defn get-projects []
  (->> (get-projects-raw)
       (mapv #(update % :project/owners (partial mapv (comp users/get-user :userid))))
       (mapv #(update % :project/collaborators (partial mapv (comp users/get-user :userid))))
       (mapv #(update % :project/applications (partial mapv (comp applications/get-application :id))))
       (mapv coerce-project-full)))

(defn get-projects-by-application [id]
  (->> (db/get-application-projects {:id id})
       (mapv parse-project)
       (mapv #(update % :project/owners (partial mapv (comp users/get-user :userid))))
       (mapv #(update % :project/collaborators (partial mapv (comp users/get-user :userid))))
       (mapv coerce-project-full)))

(defn getx-project-by-id [id]
  (assert id)
  (let [project (-> (db/get-project-by-id {:id id})
                    parse-project
                    (update :project/owners (partial mapv (comp users/get-user :userid)))
                    (update :project/collaborators (partial mapv (comp users/get-user :userid))))]
    (when-not (:project/id project)
      (throw (DataException. (str "project \"" id "\" does not exist") {:errors [{:type :t.actions.errors/project-does-not-exist  :args [id] :project/id id}]})))
    (coerce-project-full project)))

(defn join-project [x]
  ;; TODO alternatively we could pass in the project key
  ;; TODO alternatively we could decide which layer transforms db string into {:project/id "string"} and which layer joins the rest https://github.com/CSCfi/rems/issues/2179
  (let [project (:project x)
        project-id (if (string? project) project (:project/id project))
        project-overview (-> project-id
                             getx-project-by-id
                             (select-keys [:project/id :project/name]))]
    (-> x
        (update-existing :project (fn [_] project-overview))
        (update-existing :project (fn [_] project-overview)))))

(defn set-project! [project]
  (let [stripped-project (-> project
                             (update :project/owners (partial mapv #(select-keys % [:userid])))
                             validate-project)]
    (db/set-project! {:id (:project/id project)
                      :data (json/generate-string stripped-project)})))

(defn update-project! [id update-fn]
  (let [id (:project/id id id)
        project (getx-project-by-id id)]
    (set-project! (update-fn project))))

(defn get-all-project-roles [userid]
  (when (some #(contains? (set (map :userid (:project/owners %))) userid)
              (get-projects-raw))
    #{:project-owner}))

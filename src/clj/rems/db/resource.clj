(ns rems.db.resource
  (:require [rems.db.catalogue :as catalogue]
            [rems.db.core :as db])
  (:import (org.postgresql.util PSQLException)))

(defn get-resource [id]
  (-> {:id id}
      db/get-resource
      db/assoc-active))

(defn get-resources [filters]
  (->> (db/get-resources)
       (map db/assoc-active)
       (db/apply-filters filters)))

(defn duplicate-resid? [^Exception e]
  (let [cause (.getCause e)]
    (and (instance? PSQLException cause)
         (.getServerErrorMessage ^PSQLException cause)
         (= "duplicate key value violates unique constraint \"resource_resid_u\""
            (.getMessage (.getServerErrorMessage ^PSQLException cause))))))

(defn create-resource! [{:keys [resid organization licenses]} user-id]
  (try
    (let [id (:id (db/create-resource! {:resid resid
                                        :organization organization
                                        :owneruserid user-id
                                        :modifieruserid user-id}))]
      (doseq [licid licenses]
        (db/create-resource-license! {:resid id
                                      :licid licid}))
      {:success true
       :id id})
    (catch Exception e
      (if (duplicate-resid? e)
        {:success false
         :errors [{:type :t.administration.errors/duplicate-resid :resid resid}]}
        (throw e)))))

(defn update-resource! [command]
  (let [catalogue-items (->> (catalogue/get-localized-catalogue-items {:resource-id (:id command) :archived false})
                             (map #(select-keys % [:id :title :localizations])))]
    (if (and (:archived command) (seq catalogue-items))
      {:success false
       :errors [{:type :t.administration.errors/resource-in-use :catalogue-items catalogue-items}]}
      (do
        (db/set-resource-state! (select-keys command [:id :enabled :archived]))
        {:success true}))))

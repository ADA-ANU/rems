(ns rems.db.cadredb.cannedresponses
  (:require [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.applications :as applications]
            [rems.db.organizations :as organizations]
            [rems.service.util :as util]
            [rems.db.users :as users]
            [medley.core :refer [update-existing]]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CannedResponse
  {:id s/Int
   :orgid s/Str
   :response s/Str
   :title s/Str
   :created_at DateTime
   :updated_at DateTime
   :enabled s/Bool
   (s/optional-key :tags) [s/Str]})

(s/defschema CannedResponseTag
  {:id s/Int
   :orgid s/Str
   :tag s/Str
   :created_at DateTime
   :updated_at DateTime
   :enabled s/Bool
   (s/optional-key :responses) [s/Int]})

(s/defschema CannedResponseMapping
  {:id s/Int
   :responseid s/Int
   :tagid s/Int})

(defn- parse-response [raw]
  (-> raw
      (update-existing :tags json/parse-string)))

(defn- parse-tag [raw]
  (-> raw
      (update-existing :responses json/parse-string)))

(defn- cannedresponse-application? [application]
  (some #{:handler} (:application/roles application)))

(defn get-the-applications [user-id]
  (->> (applications/get-all-applications user-id)
       (filter cannedresponse-application?)))

(defn create-cannedresponse-mapping! [data]
  (if-let [id (db/tag-canned-response! data)]
    {:success (not (nil? id))
     :id (:id id)}
    {:success false
     :errors [{:type :t.create-cannedresponsemapping.errors/invalid-data}]}))

(defn create-cannedresponse! [data]
  (if (:orgid data)
    (let [tags (:tags data)
          canned-data (dissoc data :tags)
          organization (organizations/getx-organization-by-id (:orgid data))]
      (util/check-allowed-organization! organization)
      (if-let [id (db/add-canned-response! data)]
        (if (not (nil? id))
          (if (not (nil? tags))
            (doseq [tag tags]
              (create-cannedresponse-mapping! {:tagid tag :responseid (:id id)}))
            {:success true
             :id (:id id)})
          {:success false
           :errors [{:type :t.create-cannedresponse.errors/unable-to-generate}]})
        {:success false
         :errors [{:type :t.create-cannedresponse.errors/invalid-data}]}))
    {:success false
     :errors [{:type :t.create-cannedresponse.errors/no-org-id}]}))

(defn create-tag! [data]
  (if (:orgid data)
    (let [organization  (organizations/getx-organization-by-id (:orgid data))]
      (util/check-allowed-organization! organization)
      (if-let [id (db/add-canned-response-tag! data)]
        {:success (not (nil? id))
         :id (:id id)}
        {:success false
         :errors [{:type :t.create-cannedresponsetag.errors/invalid-data}]}))
    {:success false
     :errors [{:type :t.create-cannedresponsetag.errors/no-org-id}]}))

(defn delete-cannedresponse-mapping! [data]
  (db/delete-tag-canned-response! data)
  {:success true})

(defn set-cannedresponse-enabled! [data]
  (if-let [id (db/set-canned-response-enabled! data)]
    {:success (not (nil? id))
     :id (:id id)}
    {:success false
     :errors [{:type :t.create-cannedresponseenabled.errors/invalid-data}]}))

(defn set-cannedresponse-tag-enabled! [data]
  (if-let [id (db/set-canned-response-tag-enabled! data)]
    {:success (not (nil? id))
     :id (:id id)}
    {:success false
     :errors [{:type :t.create-cannedresponsetagenabled.errors/invalid-data}]}))

(defn get-cannedresponses  [cmd]
  (if-let [cannedresponses (db/get-canned-responses cmd)]
    (if (< 0 (count cannedresponses))
      {:success true
       :cannedresponses (->> cannedresponses (mapv parse-response))}
      {:success false
       :errors [{:type :t.get-comment.errors/no-cannedresponses}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-cannedresponses}]}))

(defn get-cannedresponse-tags  [cmd]
  (if-let [cannedresponsetags (db/get-canned-response-tags cmd)]
    (if (< 0 (count cannedresponsetags))

      {:success true
       :cannedresponsetags (->> cannedresponsetags (mapv parse-tag))}
      {:success false
       :errors [{:type :t.get-comment.errors/no-cannedresponsetags}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-cannedresponsetags}]}))

(defn get-cannedresponse-mapping [cmd]
  (if-let [cannedresponsemapping (db/get-canned-response-mapping cmd)]
    (if (< 0 (count cannedresponsemapping))
      {:success true
       :cannedresponsemapping cannedresponsemapping}
      {:success false
       :errors [{:type :t.get-comment.errors/no-cannedresponsemappings}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-cannedresponsemappings}]}))

(defn get-app-cannedresponses [appid userid]
  (if-let [allmyapps (get-the-applications userid)]
    (if (contains? (set (map :application/id allmyapps)) appid)
      (get-cannedresponses {:appid appid})
      {:success false
       :errors [{:type :t.get-app-cannedresponses.errors/no-app-cannedresponses}]})
    {:success false
     :errors [{:type :t.get-app-cannedresponses.errors/no-app-cannedresponses}]}))





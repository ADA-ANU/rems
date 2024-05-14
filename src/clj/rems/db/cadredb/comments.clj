(ns rems.db.cadredb.comments
  (:require [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.applications :as applications]
            [rems.db.users :as users]
            [medley.core :refer [update-existing]]
            [rems.schema-base :as schema-base]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CommentAttachment
  {:id s/Int})

(s/defschema Comment
  {:id s/Int
   (s/optional-key :appid) s/Int
   :created_by schema-base/UserWithAttributes
   (s/optional-key :addressed_to) schema-base/UserWithAttributes
   :created_at DateTime
   (s/optional-key :read_at) DateTime
   :commenttext s/Str
   (s/optional-key :attachments) [CommentAttachment]})

(defn- remove-nils [m]
  (let [f (fn [x]
            (if (map? x)
              (let [kvs (filter (comp not nil? second) x)]
                (if (empty? kvs) nil (into {} kvs)))
              x))]
    (clojure.walk/postwalk f m)))

(defn- jsonattach [comm]
  (if (:attachments comm)
    (-> comm
        (assoc :commentattrs (json/generate-string (:attachments comm)))
        (dissoc :attachments))
    comm))

(defn- join-dependencies [comm]
  (when comm
    (-> comm
        (update-existing :addressed_to users/get-user)
        (update-existing :created_by users/get-user)
        (cond-> (:commentattrs comm) (assoc :attachments (json/parse-string (:commentattrs comm))))
        (cond-> (:commentattrs comm) (dissoc :commentattrs)))))

(defn create-comment! [data]
  (cond (:appid data)
        (if-let [allmyapps (applications/get-my-applications (:userid data))]
          (if (contains? (set (map :application/id allmyapps)) (:appid data))
            (if-let [id (db/add-comment! (jsonattach data))]
              {:success (not (nil? id))
               :comment/id (:id id)}
              {:success false
               :errors [{:type :t.create-comment.errors/invalid-data}]})
            {:success false
             :errors [{:type :t.create-comment.errors/no-app-permission}]})
          {:success false
           :errors [{:type :t.create-comment.errors/no-app-id}]})
        (and (:useridto data) (not (:appid data)))
        (if-let [id (db/add-comment! (jsonattach data))]
          {:success (not (nil? id))
           :comment/id (:id id)}
          {:success false
           :errors [{:type :t.create-comment.errors/invalid-data}]})
        :else
        {:success false
         :errors [{:type :t.create-comment.errors/invalid-data}]}))


(defn get-comments  [cmd]
  (if-let [comments (db/get-comments cmd)]
    (if (< 0 (count comments))
      {:success true
       :comments (mapv join-dependencies (remove-nils comments))}
      {:success false
       :errors [{:type :t.get-comment.errors/no-comments}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-comments}]}))

(defn get-app-comments [appid userid]
  (if-let [allmyapps (applications/get-my-applications userid)]
    (if (contains? (set (map :application/id allmyapps)) appid)
      (get-comments {:appid appid})
      {:success false
       :errors [{:type :t.get-app-comments.errors/no-app-comments}]})
    {:success false
     :errors [{:type :t.get-app-comments.errors/no-app-comments}]}))

(defn markread-comment! [cmd]
  (if-let [comments (db/get-comments cmd)]
    (if (< 0 (count comments))
      (if (db/markread-comment! cmd)
        {:success true
         :comment/id (:id cmd)}
        {:success false
         :errors [{:type :t.get-comment.errors/couldnt-update}]})
      {:success false
       :errors [{:type :t.get-comment.errors/no-comments}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-comments}]}))

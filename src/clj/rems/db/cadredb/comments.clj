(ns rems.db.cadredb.comments
  (:require [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema Comment
  {:id s/Int
   (s/optional-key :appid) s/Int
   :created_by s/Str
   :addressed_to s/Str
   :created_at DateTime
   (s/optional-key :read_at) DateTime
   :commenttext s/Str})

(defn- remove-nils [m]
  (let [f (fn [x]
            (if (map? x)
              (let [kvs (filter (comp not nil? second) x)]
                (if (empty? kvs) nil (into {} kvs)))
              x))]
    (clojure.walk/postwalk f m)))

(defn create-comment! [data]
  (if-let [id (db/add-comment! data)]
      {:success (not (nil? id))
       :comment/id (:id id)}
      {:success false
       :errors [{:type :t.create-comment.errors/invalid-data}]}))

(defn get-comments  [cmd]
  (if-let [comments (db/get-comments cmd)]
      (if (< 0 (count comments))
       {:success true
       :comments (remove-nils comments)}
      {:success false
       :errors [{:type :t.get-comment.errors/no-comments}]})
    {:success false
       :errors [{:type :t.get-comment.errors/no-comments}]}))

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

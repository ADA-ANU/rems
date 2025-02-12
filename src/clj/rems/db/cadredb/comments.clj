(ns rems.db.cadredb.comments
  (:require [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.applications :as applications]
            [rems.db.users :as users]
            [medley.core :refer [update-existing]]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CommentAttachment
  {:id s/Int
   (s/optional-key :filename) s/Str})

(s/defschema CommentReadBy
  {:userid s/Str
   :readat DateTime})

(s/defschema Comment
  {:id s/Int
   (s/optional-key :appid) s/Int
   :created_by schema-base/UserWithAttributes
   (s/optional-key :addressed_to) schema-base/UserWithAttributes
   :created_at DateTime
   (s/optional-key :read_at) DateTime
   :commenttext s/Str
   (s/optional-key :readby) [CommentReadBy]
   (s/optional-key :attachments) [CommentAttachment]})

(defn- comment-application? [application]
  (some #{:applicant :member :handler :decider} (:application/roles application)))

(defn get-the-applications [user-id]
  (->> (applications/get-all-applications user-id)
       (filter comment-application?)))

(def ^:private coerce-Comment
  (coerce/coercer! Comment json/coercion-matcher))

(defn- remove-nils [m]
  (let [f (fn [x]
            (if (map? x)
              (let [kvs (filter (comp not nil? second) x)]
                (if (empty? kvs) nil (into {} kvs)))
              x))]
    (clojure.walk/postwalk f m)))

(defn- jsonattach [comm]
  (-> comm
      (assoc :commentattrs
             (json/generate-string
              (merge {:attachments (:attachments comm)}
                     {:readby (:readby comm)})))
      (dissoc :attachments)))

(defn- join-dependencies [comm]
  (when comm
    (-> comm
        (update-existing :addressed_to users/get-user)
        (update-existing :created_by users/get-user)
        (cond-> (:commentattrs comm) (assoc :attachments (:attachments (json/parse-string (:commentattrs comm)))))
        (cond-> (:commentattrs comm) (assoc :readby (:readby (json/parse-string (:commentattrs comm)))))
        (cond-> (:commentattrs comm) (dissoc :commentattrs))
        remove-nils
        coerce-Comment)))

(defn- add-attach-fn [att]
  (when att
    (-> att
        (assoc :filename (:filename (db/get-attachment-metadata att))))))

(defn- add-attach-filename [comm]
  (when comm
    (-> comm
        (update-in [:attachments] (partial map add-attach-fn)))))

(defn create-comment! [data]
  (cond (:appid data)
        (if-let [allmyapps (get-the-applications (:userid data))]
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
       :comments (remove-nils (mapv add-attach-filename (mapv join-dependencies comments)))}
      {:success false
       :errors [{:type :t.get-comment.errors/no-comments}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-comments}]}))

(defn get-app-comments [appid userid]
  (if-let [allmyapps (get-the-applications userid)]
    (if (contains? (set (map :application/id allmyapps)) appid)
      (get-comments {:appid appid})
      {:success false
       :errors [{:type :t.get-app-comments.errors/no-app-comments}]})
    {:success false
     :errors [{:type :t.get-app-comments.errors/no-app-comments}]}))

(defn get-comments-only  [cmd]
  (let [comments (db/get-comments cmd)]
    (if (< 0 (count comments))
      (remove-nils (mapv add-attach-filename (mapv join-dependencies comments)))
      comments)))

(defn get-every-app-comments [userid]
  (if-let [allmyapps (get-the-applications userid)]
    (let [app-comments (mapcat #(get-comments-only {:appid %}) (map :application/id allmyapps))]
      {:success true
       :comments app-comments})
    {:success false
     :errors [{:type :t.get-every-app-comments.errors/no-applications}]}))

(defn- markread [cmd comm]
  (jsonattach (-> comm
                  (update :readby conj {:userid (:addressed_to cmd) :readat (DateTime/now)}))))

(defn markread-comment! [cmd]
  (if-let [comments (db/get-comments (dissoc cmd :addressed_to))]
    (if (< 0 (count comments))
      (let [comm (first (remove-nils (mapv join-dependencies comments)))]
        (if (and (:addressed_to comm) (= (:addressed_to comm) (:addressed_to cmd)))
          (if (db/markread-comment! cmd)
            {:success true
             :comment/id (:id cmd)}
            {:success false
             :errors [{:type :t.get-comment.errors/couldnt-update}]})
          (if-let [allmyapps (get-the-applications (:addressed_to cmd))]
            (if (contains? (set (map :application/id allmyapps)) (:appid comm))
              (if (db/update-comment! (markread cmd comm))
                {:success true
                 :comment/id (:id cmd)}
                {:success false
                 :errors [{:type :t.get-comment.errors/couldnt-update}]})
              {:success false
               :errors [{:type :t.get-comment.errors/couldnt-update}]})
            {:success false
             :errors [{:type :t.get-comment.errors/couldnt-update}]})))
      {:success false
       :errors [{:type :t.get-comment.errors/no-comments}]})
    {:success false
     :errors [{:type :t.get-comment.errors/no-comments}]}))

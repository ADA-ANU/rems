(ns rems.db.category
  (:require [clojure.tools.logging :as log]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [rems.common.util :refer [build-index replace-key]]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [medley.core :refer [assoc-some]]))

(s/defschema CategoryData
  {:category/title schema-base/LocalizedString
   (s/optional-key :category/description) schema-base/LocalizedString
   (s/optional-key :category/children) [schema-base/CategoryId]})

(def ^:private validate-categorydata
  (s/validator CategoryData))

(s/defschema CategoryDb
  (-> {:id s/Int}
      (merge CategoryData)))

(def ^:private coerce-CategoryDb
  (coerce/coercer! CategoryDb coerce/string-coercion-matcher))

(defn- format-category [category]
  (let [categorydata (json/parse-string (:categorydata category))]
    (-> category
        (dissoc :categorydata)
        (merge categorydata))))

(def ^:private categories-cache (atom nil))

(defn reset-cache! []
  (reset! categories-cache nil))

(defn reload-cache! []
  (log/info :start #'reload-cache!)
  (let [categories (->> (db/get-categories)
                        (map #(-> (format-category %)
                                  coerce-CategoryDb
                                  (replace-key :id :category/id))))]
    (reset! categories-cache
            (build-index {:keys [:category/id] :value-fn identity} categories)))
  (log/info :end #'reload-cache!))

(defn get-category
  "Get a single category by id"
  [id]
  (when (nil? @categories-cache)
    (reload-cache!))
  (get @categories-cache id))

(defn get-categories
  "Get all categories"
  []
  (when (nil? @categories-cache)
    (reload-cache!))
  (vals @categories-cache))

(defn- get-categorydata [category]
  (-> {:category/title (:category/title category)}
      (assoc-some :category/description (:category/description category))
      (assoc-some :category/children (:category/children category))))

(defn- categorydata->json [category]
  (-> (get-categorydata category)
      validate-categorydata
      json/generate-string))

(defn create-category! [category]
  (let [id (:id (db/create-category! {:categorydata (categorydata->json category)}))]
    (reload-cache!)
    id))

(defn update-category! [id category]
  (let [id (:id (db/update-category! {:id id
                                      :categorydata (categorydata->json category)}))]
    (reload-cache!)
    id))

(defn delete-category! [id]
  (db/delete-category! {:id id})
  (reload-cache!))

(defn- enrich-category [category]
  (let [id (:category/id category)
        unknown-category {:category/id id
                          :category/title {:fi "Tuntematon kategoria"
                                           :sv "Okänd kategori"
                                           :en "Unknown category"}}]
    (if-let [category (get-category id)]
      (select-keys category [:category/id :category/title :category/description :category/children])
      unknown-category)))

(defn enrich-categories [categories]
  (mapv enrich-category categories))
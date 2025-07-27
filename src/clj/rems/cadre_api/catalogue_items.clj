(ns rems.cadre-api.catalogue-items
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.catalogue :as catalogue]
            [rems.db.core :as db]
            [ring.util.http-response :refer :all]
            [rems.util :refer [getx-user-id]]
            [schema.core :as s]))

(s/defschema GetCatalogueItemsResponse
  [schema/CatalogueItem])

(def cadre-catalogue-items-api
  (context "/cadre-catalogue-items" []
    :tags ["CADRE catalogue items"]

    (GET "/" []
      :summary "Get catalogue items with Limit and Offset options."
      :roles #{:logged-in}
      :query-params [{resource :- (describe s/Str "resource id (optional)") nil}
                     {expand :- (describe s/Str "expanded additional attributes (optional), can be \"names\"") nil}
                     {archived :- (describe s/Bool "whether to include archived items") false}
                     {disabled :- (describe s/Bool "whether to include disabled items") false}
                     {expired :- (describe s/Bool "whether to include expired items") false}
                     {limit :- (describe s/Int "the number of records to return (optional)") nil}
                     {offset :- (describe s/Int "starts on record OFFSET+1 (optional)") nil}
                     {associated :- (describe s/Bool "return only associated catalogue items") false}]
      :return GetCatalogueItemsResponse
      (ok (db/apply-filters
           (merge (when-not expired {:expired false})
                  (when-not disabled {:enabled true})
                  (when-not archived {:archived false}))
           (catalogue/get-localized-catalogue-items (merge (when associated {:userid (getx-user-id)})
                                                           {:resource resource
                                                            :expand-names? (str/includes? (or expand "") "names")
                                                            :expand-catalogue-data? true
                                                            :archived archived
                                                            :limit limit
                                                            :offset offset})))))))

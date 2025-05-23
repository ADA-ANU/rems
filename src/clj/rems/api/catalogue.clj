(ns rems.api.catalogue
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.service.catalogue :as catalogue]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.auth.util :refer [throw-forbidden throw-unauthorized]]
            [rems.config :refer [env]]
            [rems.common.roles :as roles]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema GetCatalogueResponse
  [schema/CatalogueItem])

(s/defschema GetCatalogueTreeResponse
  {:roots [(s/either schema/CategoryTree
                     schema/CatalogueItem)]}) ; catalogue items without categories end up on the root level

(def catalogue-api
  (context "/catalogue" []
    :tags ["catalogue"]
    (GET "/" []
      :summary "Get the catalogue of items for the UI (does not include archived items)"
      :return GetCatalogueResponse
      (cond
        (or (:catalogue-is-public env)
            (roles/has-roles? :logged-in))
        (ok (catalogue/get-localized-catalogue-items (merge {:archived false}
                                                            (when-not (apply roles/has-roles? roles/+admin-read-roles+)  ; only admins get enabled and disabled items
                                                              {:enabled true}))))

        (not (roles/has-roles? :logged-in))
        (throw-unauthorized)

        :else
        (throw-forbidden)))

    (GET "/tree" []
      :summary "Get the catalogue of items in a tree for the UI (does not include archived items) (EXPERIMENTAL)"
      :return GetCatalogueTreeResponse
      (cond
        (or (:catalogue-is-public env)
            (roles/has-roles? :logged-in))
        (ok (catalogue/get-catalogue-tree (merge {:archived false
                                                  :expand-catalogue-data? true
                                                  :empty false}
                                                 (when-not (apply roles/has-roles? roles/+admin-read-roles+)  ; only admins get enabled and disabled items
                                                   {:enabled true}))))

        (not (roles/has-roles? :logged-in))
        (throw-unauthorized)

        :else
        (throw-forbidden)))

    (GET "/resource" []
      :summary "Get a specific catalogue of item"
      :query-params [{resource :- (describe s/Str "resource id") ""}]
      :responses {200 {:schema schema/CatalogueItemFound}
                  404 {:schema s/Any :description "Not found"}}
      (if-let [it (first (catalogue/get-localized-catalogue-items {:resource resource
                                                                   :enabled true}))]  ; TODO, hotfix because path param fails for string with slashes
        (ok {:success true})
        (not-found-json-response)))))

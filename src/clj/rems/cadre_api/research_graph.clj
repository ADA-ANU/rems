(ns rems.cadre-api.research-graph
  (:require [compojure.api.sweet :refer :all]
            [clj-http.client :as client]
            [rems.api.util] ; required for route :roles
            [rems.api.util :refer [not-found-json-response]]
            [rems.config :refer [env]]
            [rems.service.cadre.util :as utils]
            [rems.service.cadre.research-graph :as rg]
            [rems.service.comanage :as comanage]
            [rems.util :refer [getx]]
            [schema.core :as s]))

;;Filter specified nodes, to fetch the details associated to the user
(defn get-associated-details-of-user [data keys]
  (filter #(contains? (set keys) (:key %)) data))

(defn matching-links [data-json orcid-id]
  (mapv :to (filter #(= (:from %) orcid-id) data-json)))

(def nodes-relationships-map {:datasets "researcher-dataset"
                              :grants "researcher-grant"
                              :organisations "researcher-organisation"
                              :publications "researcher-publication"
                              :researchers "researcher-researcher"})

(s/defschema NodeOptions
  (s/enum "datasets" "grants" "organisations" "publications" "researchers"))

(def research-graph-api
  (context "/research-graph" []
    :tags ["researchgraph"]

    (GET "/orcid-details" request
      :summary "Fetches the full Research Graph details of the user based on ORCID."
      :query-params [{user-id :- (describe s/Str "The user's CADRE identifier") nil}]
      :roles #{:owner :organization-owner :reporter :handler}
      (let [user-details (utils/map-type-to-identity (:Identifier (comanage/get-user user-id)))]
        (if (empty? user-details)
          (not-found-json-response)
          (if (nil? (get user-details "orcid"))
            (not-found-json-response)
            (ok (rg/orcid-details-from-research-graph (get user-details "orcid")))))))))

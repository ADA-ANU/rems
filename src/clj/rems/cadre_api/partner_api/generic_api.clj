(ns rems.cadre-api.partner-api.generic-api
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [rems.util :refer [getx getx-user-id get-user-id]]
            [clj-http.client :as client]
            [schema.core :as s]))


(def partner-generic-api
  (context "/partner" []
    :tags ["partner"]

    (GET "/user-details" request
      :summary "Fetches the details of any user of CADRE platform"
      :roles #{:owner :organization-owner}
      :query-params [userid :- (describe s/Str "Show details for this user")]

      (when (:log-authentication-details env)
          (log/info "userid === " userid))

        (let [response-json (users/fetch-user-profile userid)]
          (if (json/empty-json? response-json)
            {:status 404
             :headers {"Content-Type" "application/json"}
             :body (cheshire-json/encode {:error {:code "Not Found"
                                                  :message (str "user-id " userid " not found.")}})}
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body response-json})))
))
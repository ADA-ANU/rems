(ns rems.cadre-api.cadre-users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.cadredb.users :as users]
            [rems.service.comanage :as comanage]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [rems.api.util :refer [unprocessable-entity-json-response]] ; required for route :roles
            [clojure.tools.logging :as log]
            [rems.api.schema :as schema]
            [rems.util :refer [get-user-id]]))

(def cadre-users-api
  (context "/cadre-users" []
           :tags ["cadre-users"]

           (GET "/role" request
                :summary "Get role of the current logged-in user"
                :roles #{:logged-in}
                (let [user-id (get-user-id)
                      response-json (users/fetch-logged-in-user-role user-id)]
                  (when (:log-authentication-details env)
                    (log/info "response-json === " response-json))
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body response-json}))

           (POST "/unlink-orcid" []
                 :summary "Unlink orcid record from comanage registry if exists"
                 :roles #{:logged-in}
                 :return schema/SuccessResponse
                 (let [identifier (comanage/get-orcid-identifier (get-user-id))]
                   (if (= false identifier) 
                     (unprocessable-entity-json-response "orcid identifier not found")
                     (do (comanage/unlink-orcid (get-user-id))       
                         (ok {:success true})))))))

(ns rems.cadre-api.partner-api.generic-api
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.cadredb.users :as users]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [rems.util :refer [getx getx-user-id get-user-id]]
            [clj-http.client :as client]
            [schema.core :as s]
            [rems.api.schema :as schema]
            [rems.schema-base :as schema-base]
            [rems.db.core :as db]))

(s/defschema AddUserTrainingsCommand
  {:organization-id (s/maybe s/Str)
   :user-email-id (s/maybe s/Str)
   :given-name (s/maybe s/Str)
   :family-name (s/maybe s/Str)
   :data (s/maybe s/Any)
   })

(def partner-generic-api
  (context "/partner" []
    :tags ["partner"]

    (GET "/user-details" request
      :summary "Fetches the details of any user of CADRE platform"
      :roles #{:owner :organization-owner}
      :query-params [user-email-id :- (describe s/Str "Input the email-id of the user, who details are to be viewed.")]

      (when (:log-authentication-details env)
        (log/info "user-email-id === " user-email-id))

      (let [response-json (users/fetch-user-details-based-on-user-email-id user-email-id)]
        (if (json/empty-json? response-json)
          {:status 404
           :headers {"Content-Type" "application/json"}
           :body (cheshire-json/encode {:error {:code "Not Found"
                                                :message (str "User with user-email-id '" user-email-id "', not found!!!")}})}
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body response-json})))

    (POST "/trainings/add-user-training-details" request
      :summary "Push training details of the user from the Partner training platforms into CADRE" 
      :roles #{:owner :organization-owner}
      ;;:query-params [organization-id :- (describe s/Str "Input the Organization ID of the partner platform")
                     ;;user-email-id :- (describe s/Str "Input the email-id of the user, whose training details are to be saved in CADRE")] 
      :body [reqbody AddUserTrainingsCommand]
      
      (when (:log-authentication-details env)
          (log/info "#### add-user-training-details ####")
          ;;(log/info "organization-id === " organization-id)
          ;;(log/info "user-email-id === " user-email-id)
          (log/info "organization-id2 === " (:organization-id reqbody))
          (log/info "user-email-id2 === " (:user-email-id reqbody))
          (log/info "reqbody == " reqbody)
          (log/info "json/generate-string reqbody == " (json/generate-string reqbody)))

        (when reqbody
          (try
            (let [db-response (db/save-user-trainings-details! {:organization-id (:organization-id reqbody)
                                                                :user-email-id (:user-email-id reqbody)
                                                                :data (json/generate-string reqbody)})]
              (when (:log-authentication-details env)
                (log/info "db-response == " db-response)
                (log/info "db-response == " (:flag db-response)))
              (ok {:success true}))
            (catch Exception e
              (log/error "Error invoking API add-user-training-details :" (.getMessage e))))))))

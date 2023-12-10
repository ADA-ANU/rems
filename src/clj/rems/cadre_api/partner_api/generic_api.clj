(ns rems.cadre-api.partner-api.generic-api
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.cadredb.users :as users]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [schema.core :as s]
            [rems.util :refer [getx get-user-id]]
            [rems.db.core :as db]))

(def YYYY-MM-DD-regex #"\d{4}-\d{2}-\d{2}")

(defn valid-date-or-empty? [str]
  (or (empty? str) (re-matches YYYY-MM-DD-regex str)))

(s/defschema CourseData
  {:course-id s/Str
   :course-name s/Str
   :completion-status s/Bool})

;; Note: Compojure-API will automatically validate incoming requests against that schema.
;; If the request body doesn't conform to the schema, Compojure-API will immediately return an error response to the client, 
;; and your handler logic will not be executed.Therefore, the validation errors are not caught in the try/catch block!
(s/defschema AddUserTrainingsCommand
  {:organization-short-name s/Str
   :partner-platform-user-id s/Str
   (s/optional-key :honorific) (s/maybe s/Str)
   :given-name s/Str
   (s/optional-key :middle-name) (s/maybe s/Str)
   (s/optional-key :family-name) (s/maybe s/Str)
   (s/optional-key :suffix) (s/maybe s/Str)
   (s/optional-key :affiliation) (s/maybe s/Str)
   (s/optional-key :title) (s/maybe s/Str)
   (s/optional-key :date-of-birth) (s/maybe (s/constrained s/Str valid-date-or-empty? "Must be either null or in the format 'YYYY-MM-DD'"))
   (s/optional-key :valid-from) (s/maybe (s/constrained s/Str valid-date-or-empty? "Must be either null or in the format 'YYYY-MM-DD'"))
   (s/optional-key :valid-through) (s/maybe (s/constrained s/Str valid-date-or-empty? "Must be either null or in the format 'YYYY-MM-DD'"))
   (s/optional-key :o) (s/maybe s/Str)
   (s/optional-key :ou) (s/maybe s/Str)
   (s/optional-key :manager-identifier) (s/maybe s/Str)
   (s/optional-key :sponsor-identifier) (s/maybe s/Str)
   :mail s/Str
   (s/optional-key :identifier) (s/maybe s/Str)
   (s/optional-key :telephone-number) (s/maybe s/Str)
   (s/optional-key :address) (s/maybe s/Str)
   (s/optional-key :url) (s/maybe s/Str)
   :courses [CourseData]})

(defn- parse-trainings [raw]
  (json/parse-string (:data raw)))

(defn get-trainings [query-params]
  (->> (db/get-user-trainings-details query-params)
       (mapv parse-trainings)))

(defn get-my-trainings [query-params]
  (->> (db/get-my-trainings-details query-params)
       (mapv parse-trainings)))


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
      :body [reqbody AddUserTrainingsCommand]
      (try
        (when (:log-authentication-details env)
          (log/info "#### add-user-training-details ####")
          (log/info "organization-short-name === " (:organization-short-name reqbody))
          (log/info "partner-platform-user-id === " (:partner-platform-user-id reqbody))
          (log/info "reqbody == " reqbody)
          (log/info "json/generate-string reqbody == " (json/generate-string reqbody)))
        (when reqbody
          (let [db-response (db/save-user-trainings-details! {:organization-short-name (:organization-short-name reqbody)
                                                              :partner-platform-user-id (:partner-platform-user-id reqbody)
                                                              :data (json/generate-string reqbody)})]
            (when (:log-authentication-details env)
              (log/info "db-response == " db-response)
              (log/info "db-response == " (:flag db-response)))
            (ok {:success true})))
        (catch Exception e
          (log/error "Error invoking API add-user-training-details :" (.getMessage e))
          (log/error "Type: " (.getClass e))
          (log/error "Message: " (.getMessage e))
          (log/error "PrintStackTrace: " (.printStackTrace e))
          {:status (or (some-> e ex-data :status) 500) ; default to 500 if no specific status
           :message (.getMessage e)})))

    (GET "/trainings/user-training-details" []
      :summary "Fetches training details of the specified user from partner trainings"
      :roles #{:owner :organization-owner}
      :query-params [{organization-short-name :- (describe s/Str "Short name of the organisations") nil}
                     {partner-platform-user-id :- (describe s/Str "Partner platform user id") nil}]
      :return [AddUserTrainingsCommand]
      (ok (get-trainings {:organization-short-name organization-short-name
                          :partner-platform-user-id partner-platform-user-id})))

    (GET "/trainings/my-training-details" []
      :summary "Fetches training details of the logged in user from partner trainings"
      :roles #{:logged-in}
      :return [AddUserTrainingsCommand]
      (ok (get-my-trainings {:user-id (get-user-id)})))))

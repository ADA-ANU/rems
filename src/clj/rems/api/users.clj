(ns rems.api.users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [rems.middleware :as middleware]
            [rems.schema-base :as schema-base]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]))

(s/defschema CreateUserCommand
  ;; we can't use UserWithAttributes here since UserWithAttributes
  ;; contains :notification-email which isn't part of user
  ;; attributes (but instead comes from user settings)
  {:userid schema-base/UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :organizations) [schema-base/OrganizationId]
   s/Keyword s/Any})

(s/defschema EditUserCommand CreateUserCommand)

(def users-api
  (context "/users" []
    :tags ["users"]

    (POST "/create" []
      :summary "Create or update user"
      :roles #{:owner :user-owner}
      :body [command CreateUserCommand]
      :return schema/SuccessResponse
      (users/add-user! command)
      (ok {:success true}))

    (PUT "/edit" []
      :summary "Update user"
      :roles #{:owner :user-owner}
      :body [command EditUserCommand]
      :return schema/SuccessResponse
      (users/edit-user! command)
      (ok {:success true}))

    (GET "/active" []
      :summary "List active users"
      :roles #{:owner}
      :return [schema-base/UserWithAttributes]
      (ok (middleware/get-active-users)))))

(defn get-api-key [request]
  (get-in request [:headers "x-rems-api-key"]))

(defn get-user-id [request]
  (get-in request [:headers "x-rems-user-id"]))

(def dashboard-api
  (context "/dashboard" []
    :tags ["dashboard"]
    
    (GET "/user-profile" request
      ;;:query-params [name :- String]
      
      (let [user-id-header (get-in request [:headers "x-rems-user-id"])
            api-key-header (get-in request [:headers "x-rems-api-key"])]
        
        (log/info "x-rems-user-id === " user-id-header)
        (log/info "x-rems-api-key === " api-key-header)
        
        (cond
          (empty? user-id-header)
          (do
            ;; x-rems-user-id is either missing or empty
            (log/info "x-rems-user-id is missing or empty")
            {:status 400
             :headers {"Content-Type" "application/json"}
             :body (cheshire-json/encode {:error {:code "invalid_request"
                                                  :message "The request is missing a required header: x-rems-user-id"}})})

          (empty? api-key-header)
          (do
            ;; api-key-header is either missing or empty
            (log/info "api-key-header is missing or empty")
            {:status 400
             :headers {"Content-Type" "application/json"}
             :body (cheshire-json/encode {:error {:code "invalid_request"
                                                  :message "The request is missing a required header: api-key-header"}})})

          :else
          (let [response-json (users/fetch-user-profile user-id-header)]
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body response-json}))))))
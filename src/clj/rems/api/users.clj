(ns rems.api.users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [rems.config :refer [env]]
            [rems.middleware :as middleware]
            [rems.schema-base :as schema-base]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [rems.util :refer [getx-user-id get-user-id]]))

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

(def dashboard-api
  (context "/dashboard" []
    :tags ["dashboard"]

    (GET "/" request
      :summary "Get user dashboard page"
      :roles #{:logged-in}
      ;;:return schema/SuccessResponse
      (log/info "get-user-id === " (get-user-id))
      (log/info "getx-user-id === " (getx-user-id))
      (let [user-id (get-user-id)]

        (when (:log-authentication-details env)
          (log/info "user-id === " user-id))

        (let [response-json (users/get-user-dashboard-data user-id)]
          (if (json/empty-json? response-json)
            {:status 404
             :headers {"Content-Type" "application/json"}
             :body (cheshire-json/encode {:error {:code "Not Found"
                                                  :message (str "x-rems-user-id with value " user-id " not found.")}})}
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body response-json})))
      )
    
    (GET "/user-profile" request
      :summary "Fetches the details of the current logged-in user for dashboard purpose"
      :roles #{:logged-in}
      ;;:query-params [name :- String]
      
      (let [user-id (get-user-id)]
        
        (when (:log-authentication-details env)
          (log/info "user-id === " user-id))
        
          (let [response-json (users/fetch-user-profile user-id)]
            (if (json/empty-json? response-json) 
              {:status 404
               :headers {"Content-Type" "application/json"}
               :body (cheshire-json/encode {:error {:code "Not Found"
                                                    :message (str "x-rems-user-id with value " user-id " not found.")}})}
              {:status 200
                :headers {"Content-Type" "application/json"}
                :body response-json}
              ))))))
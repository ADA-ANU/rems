(ns rems.cadre_api.dashboard_homepage
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.users :as users]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [rems.util :refer [getx-user-id get-user-id]]))

(def dashboard-api
  (context "/dashboard" []
    :tags ["dashboard"]

    (GET "/" request
      :summary "Get user dashboard page"
      :roles #{:logged-in}
      ;;:return schema/SuccessResponse
      (when (:log-authentication-details env)
        (log/info "get-user-id === " (get-user-id))
        (log/info "getx-user-id === " (getx-user-id)))

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
             :body response-json}))))

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
             :body response-json}))))))
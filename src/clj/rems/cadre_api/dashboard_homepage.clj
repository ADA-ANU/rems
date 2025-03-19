(ns rems.cadre_api.dashboard_homepage
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.db.cadredb.users :as users]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]
            [rems.service.comanage :as comanage]
            [rems.json :as json]
            [rems.util :refer [getx getx-user-id get-user-id]]
            [schema.core :as s]
            [clj-http.client :as client]))

(s/defschema AcceptedTermsAndConditions
  {:terms-and-conditions [s/Int]})

(defn has-key? [m k]
  (contains? m k))

(defn is-vector-empty? [v]
  (empty? v))

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

    (GET "/terms-and-conditions" request
      :summary "Get user's terms and conditions"
      :roles #{:logged-in}
      :return s/Any
      (ok (comanage/get-terms-and-conditions-with-accepted (get-user-id))))

    (POST "/terms-and-conditions" request
      :summary "Accept list of CoTermsAndConditionsId"
      :roles #{:logged-in}
      :body [ts-and-cs AcceptedTermsAndConditions]
      :return s/Any
      (ok (comanage/post-terms-and-conditions-acceptance (get-user-id) ts-and-cs)))

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
             :body response-json}))))

    (GET "/user-profile/get-all-organization-identities" request
      :summary "Fetches the organization details of the current logged-in user by invoking comanage core API."
      :roles #{:logged-in}

      (let [user-id (get-user-id)
            comanage-registry-url (getx env :comanage-registry-url)
            comanage-registry-coid (getx env :comanage-registry-coid)
            url (str comanage-registry-url "/api/co/" comanage-registry-coid "/core/v1/people?identifier=" user-id)]

        (when (:log-authentication-details env)
          (log/info "user-id === " user-id)
          (log/info "url === " url))

        (when user-id
          (try
            (let [response (client/get url
                                       {:accept :json
                                        :basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]})]

              (when (:log-authentication-details env)
                (log/info "url == " url)
                (log/info "response - status == " (:status response))
                (log/info "response - Headers == " (:headers response))
                (log/info "response - Body == " (:body response))
                (log/info "json/parse-string of body == " (json/parse-string (:body response)))
                (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))

              (if (= 200 (:status response))
                (let [response-body (:body response)
                      parsed-json (json/parse-string response-body)
                      ;;has-first-element (has-key? parsed-json :0)
                      ;;has-org-identities (has-key? (parsed-json :0) :OrgIdentity)
                      ;;is-org-identity-empty (is-vector-empty? ((parsed-json :0) :OrgIdentity))
                      first-element (:0 parsed-json)
                      OrgIdentity (:OrgIdentity first-element)]

                  (log/info "parsed-json == " parsed-json)
                  (log/info "first-element == " first-element)
                  (log/info "OrgIdentity == " OrgIdentity)

                  (log/info "has-key? parsed-json :0 == " (has-key? parsed-json :0))
                  (log/info "has-key? first-element :OrgIdentity == " (has-key? first-element :OrgIdentity))
                  (log/info "is-vector-empty? OrgIdentity == " (is-vector-empty? OrgIdentity))

                  (-> {:status  200
                       :headers {"Content-Type" "application/json"}
                       :body (cheshire-json/generate-string OrgIdentity)}))
                (throw (ex-info "Non-200 status code returned: " {:response response}))))
            (catch Exception e
              (log/error "Error invoking CoManage Core API - /core/v1/people :" (.getMessage e)))))))))

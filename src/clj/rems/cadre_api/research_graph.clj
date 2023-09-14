(ns rems.cadre-api.research-graph
  (:require [compojure.api.sweet :refer :all]
            [clj-http.client :as client]
            [rems.api.util] ; required for route :roles
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [rems.util :refer [getx get-user-id]]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [schema.core :as s]))

(def research-graph-api
  (context "/research-graph" []
    :tags ["researchgraph"]

    (GET "/get-user-full-details" request
      :summary "Fetches the full Research Graph details of the user based on ORCID."
      :query-params [{orcid :- (describe s/Str "Input the ORCID of the user.") nil}]
      :roles #{:owner :organization-owner :reporter :handler}
    
      (when (:log-authentication-details env)
        (log/info "orcid === " orcid))
    
        (when orcid
          (let [url (str (getx env :rg-augment-api-url) orcid
                         "?subscription-key=" (getx env :rg-augment-api-key))]
          (try
            (let [response (client/get url {:accept :json})]
    
              (when (:log-authentication-details env)
                (log/info "url == " url)
                (log/info "response - status == " (:status response))
                (log/info "response - Headers == " (:headers response))
                (log/info "response - Body == " (:body response))
                (log/info "json/parse-string of body == " (json/parse-string (:body response)))
                (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))
    
              (if (= 200 (:status response))
                (let [parsed-json (json/parse-string (:body response))
                      ;;nodes (:nodes parsed-json)
                      ;;datasets (:datasets nodes)
                      ;;organisations (:organisations nodes)
                      ;;publications (:publications nodes)
                      ;;researchers (:researchers nodes)
                      ;;researchers (:relationships nodes)
                      ;;researchers (:stats nodes)
                      ]
                  (log/info "parsed-json == " parsed-json)
                  (-> {:status  200
                       :headers {"Content-Type" "application/json"}
                       :body (cheshire-json/generate-string (json/parse-string (:body response)))}))
                (throw (ex-info "Non-200 status code returned: " {:response response}))))
            (catch Exception e
              (log/error "Error invoking Research Graph API - " url " : " (.getMessage e)))))))))

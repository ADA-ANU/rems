(ns rems.cadre-api.research-graph
  (:require [compojure.api.sweet :refer :all]
            [clj-http.client :as client]
            [rems.api.util] ; required for route :roles
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [rems.util :refer [getx]]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [schema.core :as s]
            [rems.db.core :as db]))

(defn valid-input-node? [node]
  (some #{node} ["datasets" "grants" "organisations" "publications" "researchers"]))

(defn valid-input-relationship? [node]
  (some #{node} ["researcher-researcher" "researcher-grant" "researcher-dataset" "researcher-publication" "researcher-organisation"]))

(defn filter-rg-json
  [rg-json-data node relationship orcid]
  (log/info "Inside filter-rg-json...")
  (log/info "orcid == " orcid)
  (when (and (not (empty? node))
             (valid-input-node? node)
             (empty? relationship))
    (let [parsed-json (json/parse-string rg-json-data)
          filtered-node (get (get (first parsed-json) :nodes) (keyword node))]
      filtered-node))
  
   (when (and (not (empty? node))
              (valid-input-node? node)
              (not (empty? relationship))
              (valid-input-relationship? relationship))
     (let [parsed-json (json/parse-string rg-json-data)
           filtered-relationship (get (get (first parsed-json) :relationships) (keyword relationship))]
       filtered-relationship))
  
  (when (and (not (empty? rg-json-data))
             (empty? node)
             (empty? relationship))
    
     (log/info "Inside 3rd when clause..")
      rg-json-data))

(def research-graph-api
  (context "/research-graph" []
    :tags ["researchgraph"]

    (POST "/get-user-full-details" request
      :summary "Fetches the full Research Graph details of the user based on ORCID."
      :query-params [{orcid :- (describe s/Str "Input the ORCID of the user.") nil}
                     {node :- (describe s/Str "(optional) Input RG Node value, to view the specific RG Node of the user. If not specified, the API response will contain of all the RG Nodes! [documentation](https://github.com/ADA-ANU/rems/tree/master/cadre-docs/research_grapg.md)") nil}
                     {relationship :- (describe s/Str "(optional) Input the :relationship value.") nil}]
      :roles #{:owner :organization-owner :reporter :handler}
      (log/info "node == " node)
      (log/info "relationship == " relationship)
      (try
        (let [row (db/fetch-most-recent-rg-data-of-user {:orcid orcid})]
          (cond
            (and (not (empty? row)) (not (empty? (:rg_json_data row))))
            (do
              (log/info "inside 1st cond..")
              (log/info "userid == " (:userid row)) 
              (let [filtered-node (filter-rg-json (:rg_json_data row) node relationship orcid)]
                (log/info "filtered-node == " filtered-node)
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body filtered-node}))
            (and (not (empty? row)) (not (empty? (:userid row))))
            (do
              (log/info "inside 2nd cond..")
              (log/info "userid == " (:userid row))
              (let [userid (:userid row)
                    url (str (getx env :rg-augment-api-url) orcid
                             "?subscription-key=" (getx env :rg-augment-api-key))
                    response (client/get url {:accept :json})]
        
                (when (:log-authentication-details env)
                  (log/info "url == " url)
                  (log/info "response - status == " (:status response))
                  (log/info "response - Headers == " (:headers response))
                  (log/info "response - Body == " (:body response)))
                (db/insert-rg-data-of-user! {:userid userid
                                             :orcid orcid
                                             :rg_json_data (:body response)})
                
                (let [filtered-node (filter-rg-json (:body response) node relationship orcid)]
                  (log/info "filtered-node == " filtered-node)
                  {:status  200
                   :headers {"Content-Type" "application/json"}
                   :body (:body response)})))
            :else
            {:status 404
             :body "The requested ORCiD doesn't belong to any of the CADRE Users!"}))
             (catch Exception e
                     (log/error "Error invoking Research Graph API")
                     (log/error "Type: " (.getClass e))
                     (log/error "Message: " (.getMessage e))
                     (log/error (.printStackTrace e))
               {:status 500
                :title "Server or System error occurred!"
                :message "Something went wrong! We are working on fixing the issue."})))))

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

;;Filter specified nodes, to fetch the details associated to the user
(defn get-associated-details-of-user [data keys]
  (filter #(contains? (set keys) (:key %)) data))

(defn matching-links [data-json orcid-id]
  (mapv :to (filter #(= (:from %) orcid-id) data-json)))

(def nodes-relationships-map {:datasets "researcher-dataset"
                              :grants "researcher-grant"
                              :organisations "researcher-organisation"
                              :publications "researcher-publication"
                              :researchers "researcher-researcher"})

(defn valid-input-node? [node]
  (some #{node} ["datasets" "grants" "organisations" "publications" "researchers"]))

(defn filter-rg-json
  [rg-json-data node filter-node orcid]
  (log/info "Inside filter-rg-json...")
  (log/info "rg-json-data == " rg-json-data)
  (log/info "node == " node)
  (log/info "filter-node == " filter-node)
  (log/info "orcid == " orcid)

   (cond 
     ;;Cond:1) Just fetch a paticular specified node from the RG JSON, No further filtering in the node!
     (and (not (empty? node))
          (valid-input-node? node)
          (not filter-node))
     (do
       (log/info "Inside 1st cond..")
       (let [parsed-json (json/parse-string rg-json-data)
             filtered-node (get (get (first parsed-json) :nodes) (keyword node))]
         (log/info "parsed-json == " parsed-json)
         (log/info "filtered-node == " filtered-node)
         filtered-node))
     ;;Cond:2) Fetch a paticular specified node from the RG JSON, and filter the node to find details just associated to the User.
     (and (not (empty? node))
          (valid-input-node? node)
          filter-node)
     (do
       (log/info "Inside 2nd cond..")
       (let [parsed-json (json/parse-string rg-json-data)
             filtered-node (get (get (first parsed-json) :nodes) (keyword node))
             filtered-relationship (get (get (first parsed-json) :relationships) (keyword (get nodes-relationships-map (keyword node))))]
         (log/info "parsed-json == " parsed-json)
         (log/info "filtered-relationship == " filtered-relationship)
         (if (empty? filtered-relationship)
           filtered-relationship
           (let [keys-list (matching-links filtered-relationship (str "researchgraph.com/orcid/" orcid))
                 associated-details (get-associated-details-of-user filtered-node keys-list)]
             (log/info "keys-list == " keys-list)
             (log/info "associated-details == " associated-details)
             associated-details))))
     ;;Else: return the entire RG User details as it is without filtering!
     :else
     (do
       (log/info "Inside else..")
       (json/parse-string rg-json-data))))

(def research-graph-api
  (context "/research-graph" []
    :tags ["researchgraph"]

    (POST "/get-user-full-details" request
      :summary "Fetches the full Research Graph details of the user based on ORCID."
      :query-params [{orcid :- (describe s/Str "Input the ORCID of the user.") nil}
                     {node :- (describe s/Str "(optional) Input RG Node value, to view the specific RG Node of the user. If not specified, the API response will contain of all the RG Nodes! [documentation](https://github.com/ADA-ANU/rems/tree/master/cadre-docs/research_graph.md)") nil}
                     {filter-node :- (describe s/Bool "(optional) Filters the specified node to fetch the details directly associted to the user.") false}]
      :roles #{:owner :organization-owner :reporter :handler}
      (log/info "orcid == " orcid)
      (log/info "node == " node)
      (log/info "filter-node == " filter-node)
      (try
        (let [row (db/fetch-most-recent-rg-data-of-user {:orcid orcid})]
          (cond
            ;;Cond-1: Cache data of user available in DB
            (and (not (empty? row)) (not (empty? (:rg_json_data row))))
            (do
              (log/info "################## 1st cond ###############")
              (log/info "userid == " (:userid row)) 
              (let [filtered-node (filter-rg-json (:rg_json_data row) node filter-node orcid)]
                (log/info "filtered-node == " filtered-node)
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body (cheshire-json/generate-string filtered-node)}))
            ;;Cond-2: Cached RG User data NOT Available in DB, and the ORCiD Belongs to CADRE User
            (and (not (empty? row)) (not (empty? (:userid row))))
            (do
              (log/info "################# 2nd cond ##################")
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
                
                (let [filtered-node (filter-rg-json (:body response) node filter-node orcid)]
                  (log/info "filtered-node == " filtered-node)
                  {:status  200
                   :headers {"Content-Type" "application/json"}
                   :body (cheshire-json/generate-string filtered-node)})))
            ;;Else: The ORCiD doesn't belong to any of the CADRE User
            :else
            (do
              (log/info "################# Else ##################")
              {:status 404
               :body "The requested ORCiD doesn't belong to any of the CADRE Users!"})))
               (catch Exception e
                     (log/error "Error invoking Research Graph API")
                     (log/error "Type: " (.getClass e))
                     (log/error "Message: " (.getMessage e))
                     (log/error (.printStackTrace e))
               {:status 500
                :title "Server or System error occurred!"
                :message "Something went wrong! We are working on fixing the issue."})))))

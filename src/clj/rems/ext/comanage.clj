(ns rems.ext.comanage
  "Utilities for interfacing with CoManage REST API.

  The function names try to match the remote API method and paths."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rems.json :as json]
            [rems.config :refer [env]]
            [rems.util :refer [getx]]))

(def ^:private +common-opts+
  {:socket-timeout 5500
   :conn-timeout 5500
   :as :json})

(defn get-group-member-id
  "Get CoManage group member id for a given co-group-id"
  [cogroupid copersonid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_group_members.json?cogroupid=" cogroupid)
          response (http/get url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (:body response)
              groups (:CoGroupMembers parsed-json)
              group-members (filterv #(= (:Id (:Person %)) copersonid) groups)
              first-group-member (first group-members)
              id (:Id first-group-member)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage GET API - " "co_group_members.json :" (.getMessage e)))))


(defn get-person-id
  "Get CoManage person id for a given user identifier"
  [userid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_people.json?coid=" (getx env :comanage-registry-coid) "&search.identifier=" userid)
          response (http/get url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (:body response)
              people (:CoPeople parsed-json)
              person (first people)
              id (:Id person)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage GET API - " "co_people.json :" (.getMessage e)))))

(defn delete-org-identity
  "Delete CoManage organisational entity"
  [orgidentityid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/org_identities/" orgidentityid  ".json?coid=" (getx env :comanage-registry-coid))
          response (http/delete url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        orgidentityid
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage DELETE API - " "org_identities.json :" (.getMessage e)))))

(defn unlink-orcid
  "Remove orcid link from user by unlinking then deleting the CoManage organisation identity for an orglink (based on user)"
  [orglink]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_org_identity_links/" (:Id orglink)  ".json?coid=" (getx env :comanage-registry-coid))
          response (http/delete url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (delete-org-identity (:OrgIdentityId orglink))
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage DELETE API - " "co_org_identity_links/" (:Id orglink) ".json :" (.getMessage e)))))

(defn get-org-identity-links
  "Get CoManage organisation identiy links for a user"
  [copersonid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_org_identity_links.json?coid=" (getx env :comanage-registry-coid) "&copersonid=" copersonid)
          response (http/get url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (:body response)
              orgs (:CoOrgIdentityLinks parsed-json)]
          orgs)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage GET API - " "co_org_identity_links.json :" (.getMessage e)))))

(defn get-orcid-identifiers
  "Get orcid identifiers (if any) from the orgidentityid"
  [org]
  (try
    (let [url (str (getx env :comanage-registry-url) "/identifiers.json?coid=" (getx env :comanage-registry-coid) "&orgidentityid=" (:OrgIdentityId org))
          response (http/get url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (:body response)
              identifiers (:Identifiers parsed-json)
              orcids (filterv #(str/includes? (:Type %) "orcid") identifiers)
              first-identifier (first orcids)
              id (if (nil? first-identifier) nil org)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage GET API - " "identifiers.json :" (.getMessage e)))))



(defn get-group-id
  "Get CoManage group id for a given entitlement resource id"
  [resourceid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_groups.json?coid=" (getx env :comanage-registry-coid))
          response (http/get url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (:body response)
              groups (:CoGroups parsed-json)
              resource-groups (filterv #(str/includes? (:Name %) resourceid) groups)
              first-resource-group (first resource-groups)
              id (:Id first-resource-group)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage GET API - " "co_groups.json :" (.getMessage e)))))


(defn post-create-or-update-permissions
  "Get add member to comanage group"
  [post]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_group_members.json")
          response (http/post url (merge +common-opts+
                                         {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]
                                          :body (json/generate-string post)}))]
      (if (= 201 (:status response))
        (let [parsed-json (:body response)
              id (:Id parsed-json)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage POST API - " "co_group_members.json :" (.getMessage e) "tried to post: " (json/generate-string post)))))


(defn delete-identifier
  "Delete a CoManage identifier"
  [identifierid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/identifiers/" identifierid ".json")
          response (http/delete url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        identifierid
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage DELETE API - " "identifiers.json :" (.getMessage e) (str (getx env :comanage-registry-url) "/identifiers/" identifierid ".json")))))


(defn delete-permissions
  "Delete a CoManage group member id"
  [cogroupmemberid]
  (try
    (let [url (str (getx env :comanage-registry-url) "/co_group_members/" cogroupmemberid ".json")
          response (http/delete url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        cogroupmemberid
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage DELETE API - " "co_group_members.json :" (.getMessage e) (str (getx env :comanage-registry-url) "/co_group_members/" cogroupmemberid ".json")))))


(defn get-user
  "Get a given user's identities"
  [user-id]
  (try
    (let [url (str (getx env :comanage-registry-url) "/api/co/" (getx env :comanage-registry-coid) "/core/v1/people?identifier=" user-id)
          response (http/get url (merge +common-opts+ {:basic-auth [(getx env :comanage-core-api-userid) (getx env :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (:body response)
              id (:0 parsed-json)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage GET API - " (.getMessage e) (str (getx env :comanage-registry-url) "/api/co/" (getx env :comanage-registry-coid) "/core/v1/people?identifier=" user-id)))))

(ns rems.ext.comanage
  "Utilities for interfacing with CoManage REST API.

  The function names try to match the remote API method and paths."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [rems.json :as json]
            [rems.util :refer [getx]]))

(def ^:private +common-opts+
  {:socket-timeout 2500
   :conn-timeout 2500
   :as :json})

(defn get-group-member-id
  "Get CoManage group member id for a given co-group-id"
  [cogroupid copersonid config]
  (try
    (let [url (str (getx config :comanage-registry-url) "/co_group_members.json?cogroupid=" cogroupid)
          response (http/get url (merge +common-opts+ {:basic-auth [(getx config :comanage-core-api-userid) (getx config :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (json/parse-string (:body response))
              groups (:CoGroupMembers parsed-json)
              group-members (filter (= copersonid (:Id :Person)) groups)
              first-group-member (first group-members)
              id (:Id first-group-member)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage API - " "co_group_members.json :" (.getMessage e)))))


(defn get-person-id
  "Get CoManage person id for a given user identifier"
  [userid config]
  (try
    (let [url (str (getx config :comanage-registry-url) "/co_people.json?coid=" (getx config :comanage-registry-coid) "&search.identifier=" userid)
          response (http/get url (merge +common-opts+ {:basic-auth [(getx config :comanage-core-api-userid) (getx config :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (json/parse-string (:body response))
              people (:CoPeople parsed-json)
              person (first people)
              id (:Id person)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage API - " "co_people.json :" (.getMessage e)))))

(defn get-group-id
  "Get CoManage group id for a given entitlement resource id"
  [resourceid config]
  (try
    (let [url (str (getx config :comanage-registry-url) "/co_groups.json?coid=" (getx config :comanage-registry-coid))
          response (http/get url (merge +common-opts+ {:basic-auth [(getx config :comanage-core-api-userid) (getx config :comanage-core-api-key)]}))]
      (if (= 200 (:status response))
        (let [parsed-json (json/parse-string (:body response))
              groups (:CoGroups parsed-json)
              resource-groups (filter (str/includes? resourceid :Name) groups)
              first-resource-group (first resource-groups)
              id (:Id first-resource-group)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage API - " "co_groups.json :" (.getMessage e)))))


(defn post-create-or-update-permissions
  "Get add member to comanage group"
  [post config]
  (try
    (let [url (str (getx config :comanage-registry-url) "/co_group_members.json")
          response (http/post url (merge +common-opts+
                                         {:basic-auth [(getx config :comanage-core-api-userid) (getx config :comanage-core-api-key)]
                                          :body post}))]
      (if (= 200 (:status response))
        (let [parsed-json (json/parse-string (:body response))
              id (:Id parsed-json)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage API - " "co_group_members.json :" (.getMessage e)))))


(defn delete-permissions
  "Delete a CoManage group member id"
  [cogroupmemberid config]
  (try
    (let [url (str (getx config :comanage-registry-url) "/co_group_members/" cogroupmemberid ".json")
          response (http/delete url (merge +common-opts+ :basic-auth [(getx config :comanage-core-api-userid) (getx config :comanage-core-api-key)]))]
      (if (= 200 (:status response))
        (cogroupmemberid)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking CoManage API - " "co_group_members.json :" (.getMessage e)))))


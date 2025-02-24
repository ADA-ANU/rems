(ns rems.cadre-api.cadre-users
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+]]
            [rems.config :refer [env]]
            [rems.service.cadre.util :as utils]
            [rems.ext.comanage :as comanage]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [rems.util :refer [get-user-id]]))

(defn handle-identity-response [identity response-json]
  (if (empty? response-json)
    (not-found-json-response)
    (if (str/blank? identity)
      (ok response-json)
      (if (nil? (get response-json identity))
        (not-found-json-response)
        (ok (get response-json identity))))))

(defn get-identity [identity response-json]
  (if (empty? response-json)
    nil
    (get response-json identity)))

(defn get-user-identity [user-id identity]
  (get-identity identity (utils/map-type-to-identity (:Identifier (comanage/get-user user-id env)))))

(defn user-orcid-map [user-ids]
  (let [identity "orcid"]
    (into {} (map (fn [user-id] [user-id (get-user-identity user-id identity)]) user-ids))))

(def cadre-users-api
  (context "/orcid" []
    :tags ["orcid"]

    (GET "/" request
      :summary "Get identity information from yourself from comanage-registry-url"
      :roles #{:logged-in}
      :return s/Any
      (let [user-id (get-user-id)
            identity "orcid"
            response-json (utils/map-type-to-identity (:Identifier (comanage/get-user user-id env)))]
        (handle-identity-response identity response-json)))

    (GET "/bulk" request
      :summary "Get identity information from a given user by their userid from comanage-registry-url"
      :roles +admin-read-roles+
      :query-params [{user-ids :- (describe [s/Str] "user ids") ""}]
      :return s/Any
      (if (empty? user-ids)
        (not-found-json-response)
        (ok (user-orcid-map user-ids))))))

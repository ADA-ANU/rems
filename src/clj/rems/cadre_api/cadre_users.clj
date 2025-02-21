(ns rems.cadre-api.cadre-users
  (:require [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+]]
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

(def cadre-users-api
  (context "/orcid" []
    :tags ["orcid"]

    (GET "/" request
      :summary "Get identity information from yourself from comanage-registry-url"
      :roles #{:logged-in}
      :return s/Any
      (let [user-id (get-user-id)
            identity "orcid"
            response-json (utils/map-type-to-identity (:Identifier (comanage/get-user user-id)))]
        (handle-identity-response identity response-json)))

    (GET "/:user" request
      :summary "Get identity information from a given user by their userid from comanage-registry-url"
      :roles +admin-read-roles+
      :path-params [user :- (describe s/Str "return permissions for this user, required")]
      :return s/Any
      (let [identity "orcid"
            response-json (utils/map-type-to-identity (:Identifier (comanage/get-user user)))]
        (handle-identity-response identity response-json)))))

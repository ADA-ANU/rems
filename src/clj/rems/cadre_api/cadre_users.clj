(ns rems.cadre-api.cadre-users
  (:require [compojure.api.sweet :refer :all]
            [rems.api.util] ; required for route :roles
            [rems.config :refer [env]]
            [rems.ext.comanage :as comanage]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [rems.util :refer [get-user-id]]))

(def cadre-users-api
  (context "/cadre-users" []
    :tags ["cadre-users"]

    (GET "/orcid" request
      :summary "Get orcid of self from comanage-registry-url"
      :roles #{:logged-in}
      (let [user-id (get-user-id)
            response-json (comanage/get-user user-id env)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body response-json}))))

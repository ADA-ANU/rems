(ns rems.service.comanage
  "Service for interfacing with CoManage REST API."
  (:require [clj-time.core :as time-core]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [medley.core :refer [find-first]]
            [rems.config :refer [env]]
            [rems.ext.comanage :as comanage]
            [rems.util :refer [getx]]))


(defn- entitlement->update
  "Converts an entitlement to a CoManage group request.

  `entitlement` – entitlement to convert"
  [entitlement config]
  {:RequestType "CoGroupMembers"
   :Version "1.0"
   :CoGroupMembers [{:Version "1.0"
                     :CoGroupId (comanage/get-group-id (:resid entitlement) config)
                     :Person {:Type "CO"
                              :Id (comanage/get-person-id (:userid entitlement) config)}
                     :Member true
                     :Owner false}]})

(defn entitlement-push [action entitlement config]
  (let [common-fields {:config config}]
    (let [response (case action
                     :add
                     (comanage/post-create-or-update-permissions (entitlement->update entitlement config) config)

                     :remove
                     (comanage/delete-permissions (comanage/get-group-member-id (comanage/get-group-id (:resid entitlement) config) (comanage/get-person-id (:userid entitlement) config) config) config))]
      response)))

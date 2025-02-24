(ns rems.service.comanage
  "Service for interfacing with CoManage REST API."
  (:require [clj-time.core :as time-core]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [medley.core :refer [find-first]]
            [rems.ext.comanage :as comanage]
            [rems.util :refer [getx]]))


(defn- entitlement->update
  "Converts an entitlement to a CoManage group request.

  `entitlement` – entitlement to convert"
  [entitlement config]
  {:RequestType "CoGroupMembers"
   :Version "1.0"
   :CoGroupMembers [{:Version "1.0"
                     :CoGroupId (comanage/get-group-id (:resid entitlement))
                     :Person {:Type "CO"
                              :Id (comanage/get-person-id (:userid entitlement))}
                     :Member true
                     :Owner false}]})

(defn entitlement-push [action entitlement config]
  (let [common-fields {:config config}]
    (let [response (case action
                     :add
                     (comanage/post-create-or-update-permissions (entitlement->update entitlement config))

                     :remove
                     (comanage/delete-permissions (comanage/get-group-member-id (comanage/get-group-id (:resid entitlement)) (comanage/get-person-id (:userid entitlement)))))]
      response)))


(defn get-orcid-org-id
  "Get CoManage organisation identity for a user if they have and orcid identifier"
  [userid]
  (let [orgs (comanage/get-org-identity-links (comanage/get-person-id userid))]
    (some #(comanage/get-orcid-identifiers %) orgs)))

(defn unlink-orcid [orglink]
  (if (= nil (:Id orglink))
    nil
    (comanage/unlink-orcid orglink)))

(defn get-user [user-id]
  (comanage/get-user user-id))

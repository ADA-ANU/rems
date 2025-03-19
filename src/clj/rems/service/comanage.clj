(ns rems.service.comanage
  "Service for interfacing with CoManage REST API."
  (:require [clj-time.core :as time-core]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [medley.core :refer [find-first]]
            [rems.ext.comanage :as comanage]
            [rems.util :refer [getx]]))



(defn- update-key [m k f & args]
  (update m k #(apply f % args)))  ;; Apply f with existing value + extra args

(defn- update-terms-and-conditions [co-terms-and-conditions accepted-terms]
  (map #(assoc % :Accepted (contains? accepted-terms (:Id %))) co-terms-and-conditions))

(defn- format-terms-and-conditions [ts-and-cs person-id]
  {:RequestType "CoTAndCAgreements"
   :Version "1.0"
   :CoTAndCAgreements (mapv (fn [n]
                              {:Version "1.0"
                               :CoTermsAndConditionsId n
                               :Person {:Type "CO"
                                        :Id person-id}})
                            ts-and-cs)})


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


(defn get-terms-and-conditions-with-accepted
  "Return comange terms and conditions including key for acceptance"
  [userid]
  (let [person-id (comanage/get-person-id userid)
        terms-and-conditions (comanage/get-terms-and-conditions)
        accepted-terms (reduce (fn [acc item]
                                 (assoc acc (:CoTermsAndConditionsId item) item))
                               {}
                               (:CoTAndCAgreements (comanage/get-accepted-terms-and-conditions person-id)))]
    (update-key terms-and-conditions :CoTermsAndConditions update-terms-and-conditions accepted-terms)))


(defn get-outstanding-terms-and-conditions
  "Return terms and conditions that are active and not deleted, and also not accepted. Otherwise nil"
  [userid]
  (let [tacs (get-terms-and-conditions-with-accepted userid)
        tnc (:CoTermsAndConditions tacs)
        tnca (filterv #(= (:Status %) "Active") tnc)
        tncb (filterv #(= (:Deleted %) false) tnca)
        tncc (filterv #(= (:Accepted %) false) tncb)]
    (if (or (empty? tncc) (every? nil? tncc))
      nil
      tncc)))

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

(defn post-terms-and-conditions-acceptance
  [userid ts-and-cs]
  (let [person-id (comanage/get-person-id userid)
        post-body (format-terms-and-conditions (:terms-and-conditions ts-and-cs) person-id)]
    (comanage/post-terms-and-conditions-acceptance post-body)))

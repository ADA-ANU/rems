(ns rems.db.invitation
  (:require [rems.common.util :refer [getx]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def InvitationData
  {(s/optional-key :invitation/id) s/Int
   :invitation/name s/Str
   :invitation/email s/Str
   :invitation/token s/Str
   :invitation/invited-by schema-base/User
   (s/optional-key :invitation/invited-user) schema-base/User
   :invitation/created DateTime
   (s/optional-key :invitation/sent) DateTime
   (s/optional-key :invitation/accepted) DateTime
   (s/optional-key :invitation/revoked) DateTime
   (s/optional-key :invitation/revoked-by) schema-base/User
   (s/optional-key :invitation/workflow) {:workflow/id s/Int}
   (s/optional-key :invitation/project) {:project/id s/Int}})

(def ^:private validate-InvitationData
  (s/validator InvitationData))

(defn create-invitation! [data]
  (let [amended (assoc data :invitation/created (DateTime/now))
        json (json/generate-string (validate-InvitationData amended))]
    (-> {:invitationdata json}
        db/add-invitation!
        :id)))

(def ^:private coerce-InvitationData
  (coerce/coercer! InvitationData json/coercion-matcher))

(defn- fix-row-from-db [row]
  (-> (:invitationdata row)
      json/parse-string
      coerce-InvitationData
      (assoc :invitation/id (:id row))))

(defn get-invitations
  [{:keys [project-id workflow-id ids token sent accepted revoked]}]
  (cond->> (db/get-invitations {:ids ids :token token})
    true (map fix-row-from-db)
    workflow-id (filter (comp #{workflow-id} :workflow/id :invitation/workflow))
    project-id (filter (comp #{project-id} :project/id :invitation/project))
    (some? sent) ((if sent filter remove) :invitation/sent)
    (some? revoked) ((if revoked filter remove) :invitation/revoked)
    (some? accepted) ((if accepted filter remove) :invitation/accepted)))

(defn accept-invitation! [userid token]
  (when-let [invitation (first (get-invitations {:token token}))]
    (let [amended (merge (dissoc invitation :invitation/id)
                         {:invitation/invited-user {:userid userid}
                          :invitation/accepted (DateTime/now)})
          json (json/generate-string (validate-InvitationData amended))]
      (db/set-invitation! {:id (:invitation/id invitation)
                           :invitationdata json}))))

(defn revoke-invitation! [userid id]
  (when-let [invitation (first (get-invitations {:ids [id]}))]
    (let [amended (merge (dissoc invitation :invitation/id)
                         {:invitation/revoked-by {:userid userid}
                          :invitation/revoked (DateTime/now)})
          json (json/generate-string (validate-InvitationData amended))]
      (db/set-invitation! {:id (:invitation/id invitation)
                           :invitationdata json}))))

(defn mail-sent! [id]
  (when-let [invitation (first (get-invitations {:ids [id]}))]
    (let [amended (merge (dissoc invitation :invitation/id)
                         {:invitation/sent (DateTime/now)})
          json (json/generate-string (validate-InvitationData amended))]
      (db/set-invitation! {:id (:invitation/id invitation)
                           :invitationdata json}))))

(defn update-invitation! [invitation]
  (when-let [old-invitation (first (get-invitations {:ids [(getx invitation :invitation/id)]}))]
    (let [amended (dissoc (merge old-invitation
                                 invitation)
                          :invitation/id)
          json (json/generate-string (validate-InvitationData amended))]
      (db/set-invitation! {:id (:invitation/id invitation)
                           :invitationdata json}))))

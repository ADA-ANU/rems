(ns rems.service.invitation
  (:require [rems.service.util :as util]
            [rems.service.cadre.util]
            [rems.db.cadredb.applications :as applications]
            [rems.db.invitation :as invitation]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.db.cadredb.projects :as projects]
            [rems.email.core :as email]
            [medley.core :refer [update-existing]]
            [rems.util :refer [secure-token]]))

(defn- join-dependencies [invitation]
  (when invitation
    (-> invitation
        (update-existing :invitation/invited-by users/join-user)
        (update-existing :invitation/revoked-by users/join-user)
        (update-existing :invitation/invited-user users/join-user))))

(defn- join-project [invitation]
  (if-let [project-id (get-in invitation [:invitation/project :project/id])]
    (let [project (projects/get-project-by-id-raw project-id)]
      (-> invitation
          (cond-> (:project/description project) (update :invitation/project conj {:project/description (:project/description project)}))
          (cond-> (:project/name project) (update :invitation/project conj {:project/name (:project/name project)}))))  invitation))

(defn- apply-user-permissions [userid invitation]
  (dissoc invitation :invitation/token))

(defn- invalid-invitation-type-error [cmd]
  (when-not (or (:workflow-id cmd) (:project-id cmd)); so far we only support invitation to workflow or project
    {:success false
     :errors [{:type :t.accept-invitation.errors/invalid-invitation-type
               :workflow-id (:workflow-id cmd)
               :project-id (:project-id cmd)}]}))

(defn- invalid-workflow-error [cmd]
  (when-let [workflow-id (:workflow-id cmd)]
    (if-let [workflow (workflow/get-workflow workflow-id)]
      ;; TODO: check for workflow status, or perhaps it's ok to invite to any workflow?
      (let [organization (:organization workflow)]
        (util/check-allowed-organization! organization))
      {:success false
       :errors [{:type :t.accept-invitation.errors/invalid-workflow :workflow-id workflow-id}]})))

(defn- invalid-project-error [cmd]
  (when-let [project-id (:project-id cmd)]
    (if-let [project (projects/get-project-by-id-raw project-id)]
      (do
        (rems.service.cadre.util/check-allowed-project! project)
        (let [project (:id project)]))
      {:success false
       :errors [{:type :t.accept-invitation.errors/invalid-project :project-id project-id}]})))

(defn get-invitations-full [cmd]
  (->> cmd
       invitation/get-invitations
       (mapv join-dependencies)
       (mapv join-project)))

(defn get-invitations [cmd]
  (->> cmd
       get-invitations-full
       (mapv (partial apply-user-permissions (:userid cmd)))))

(defn get-my-invitations [userid]
  (mapv join-project (mapv join-dependencies (invitation/get-my-invitations userid))))

(defn get-invitation-full [id]
  (->> {:ids [id]}
       get-invitations-full
       first))

(defn get-invitation [id]
  (->> {:ids [id]}
       get-invitations
       first))

(defn create-invitation! [cmd]
  (or (invalid-invitation-type-error cmd)
      (invalid-workflow-error cmd)
      (invalid-project-error cmd)
      (let [id (invitation/create-invitation! (merge {:invitation/name (:name cmd)
                                                      :invitation/email (:email cmd)
                                                      :invitation/token (secure-token)
                                                      :invitation/invited-by {:userid (:userid cmd)}}
                                                     (when-let [workflow-id (:workflow-id cmd)]
                                                       {:invitation/workflow {:workflow/id workflow-id}})
                                                     (when-let [role (:role cmd)]
                                                       {:invitation/role role})
                                                     (when-let [project-id (:project-id cmd)]
                                                       {:invitation/project {:project/id project-id}})))]
        (when id
          (email/generate-invitation-emails! (get-invitations-full {:ids [id]})))
        {:success (not (nil? id))
         :invitation/id id})))

(defn- filter-user [data userid]
  (vec (filter #(not= (:userid %) userid) data)))

(defn leave-after-invitation! [id]
  (invitation/leave-after-invitation! id))

(defn decline-invitation! [{:keys [userid useremail token]}]
  (if-let [invitation (first (invitation/get-invitations {:token token}))]
    (if-let [invite-email (get invitation :invitation/email)]
      (if (.equalsIgnoreCase invite-email useremail)
        (if (and (nil? (get invitation :invitation/declined)) (nil? (get invitation :invitation/revoked)) (nil? (get invitation :invitation/accepted)))
          (if-let [project-id (get-in invitation [:invitation/project :project/id])]
            (let [project (projects/get-project-by-id-raw project-id)
                  id (:id invitation)]
              (do
                (invitation/decline-invitation! token)
                (email/generate-decline-emails! (get-invitations-full {:ids [id]}))
                {:success true}))
            {:success false
             :errors [{:key :t.decline-invitation.errors/invalid-invitation-type}]})
          {:success false
           :errors [{:key :t.decline-invitation.errors/token-already-consumed}]})
        {:success false
         :errors [{:key :t.decline-invitation.errors/not-your-invitation}]})
      {:success false
       :errors [{:key :t.decline-invitation.errors/no-invitee-found}]})
    {:success false
     :errors [{:key :t.decline-invitation.errors/invalid-token :token token}]}))

(defn revoke-invitation! [{:keys [userid id]}]
  (if-let [invitation (first (invitation/get-invitations {:ids [id]}))]
    (if (and (nil? (get invitation :invitation/declined)) (nil? (get invitation :invitation/revoked)))
      (if-let [project-id (get-in invitation [:invitation/project :project/id])]
        (let [project (projects/get-project-by-id-raw project-id)]
          (do
            (rems.service.cadre.util/check-allowed-project! project)
            (invitation/revoke-invitation! userid id)
            (email/generate-revocation-emails! (get-invitations-full {:ids [id]}))
            (if (= (:invitation/role invitation) "owner")
              (projects/update-project! project-id (fn [project] (-> project
                                                                     (dissoc :project/invitations
                                                                             :project/applications)
                                                                     (update :project/owners #(filter-user % (get-in invitation [:invitation/invited-user :userid]))))))
              (projects/update-project! project-id (fn [project] (-> project
                                                                     (dissoc :project/invitations
                                                                             :project/applications)
                                                                     (update :project/collaborators #(filter-user % (get-in invitation [:invitation/invited-user :userid])))))))
            {:success true
             :invitation/project {:project/id (:project/id project)}}))
        {:success false
         :errors [{:key :t.revoke-invitation.errors/invalid-invitation-type}]})
      {:success false
       :errors [{:key :t.revoke-invitation.errors/already-rejected}]})
    {:success false
     :errors [{:key :t.revoke-invitation.errors/invalid-id :id id}]}))

(defn accept-invitation! [{:keys [userid useremail token]}]
  (if-let [invitation (first (invitation/get-invitations {:token token}))]
    (if (.equalsIgnoreCase (get invitation :invitation/email) useremail)
      (if-let [workflow-id (get-in invitation [:invitation/workflow :workflow/id])]
        (let [workflow (workflow/get-workflow workflow-id)
              handlers (set (map :userid (get-in workflow [:workflow :handlers])))]
          (if (contains? handlers userid)
            {:success false
             :errors [{:key :t.accept-invitation.errors.already-member/workflow}]}
            (do
              (workflow/edit-workflow! {:id workflow-id
                                        :handlers (conj handlers userid)})
              (invitation/accept-invitation! userid token)
              (applications/reload-cache!)
              {:success true
               :invitation/workflow {:workflow/id (:id workflow)}})))
        (if-let [project-id (get-in invitation [:invitation/project :project/id])]
          (if (and (nil? (get invitation :invitation/declined)) (nil? (get invitation :invitation/revoked)) (nil? (get invitation :invitation/accepted)))
            (let [cmd (projects/get-project-by-id-raw project-id)]
              (do
                (invitation/accept-invitation! userid token)
                (if (= (:invitation/role invitation) "owner")
                  (projects/update-project! project-id (fn [project] (dissoc project :project/invitations
                                                                             :project/applications)
                                                         (update project :project/owners conj {:userid userid})))
                  (projects/update-project! project-id (fn [project] (dissoc project :project/invitations
                                                                             :project/applications)
                                                         (update project :project/collaborators conj {:userid userid}))))
                {:success true
                 :invitation/project {:project/id (:project/id cmd)}}))
            {:success false
             :errors [{:key :t.accept-invitation.errors/token-already-consumed}]})
          {:success false
           :errors [{:key :t.accept-invitation.errors/invalid-invitation-type}]}))
      {:success false
       :errors [{:key :t.accept-invitation.errors/not-your-invitation}]})
    {:success false
     :errors [{:key :t.accept-invitation.errors/invalid-token :token token}]}))

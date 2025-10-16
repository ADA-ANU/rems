(ns rems.service.cadre.projects
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [medley.core :refer [assoc-some find-first]]
            [rems.service.dependencies :as dependencies]
            [rems.service.cadre.util :refer [check-allowed-project! check-allowed-this-project! check-project-membership! may-view-projects?]]
            [rems.service.invitation :as invitation]
            [rems.auth.util]
            [rems.db.cadredb.applications :as applications]
            [rems.db.core :as db]
            [rems.db.cadredb.projects :as projects]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.util :refer [getx-user-email getx-user-id get-value-as-lower-case]]))

(defn- apply-user-permissions [userid projects]
  (filter (fn [project]
            (may-view-projects? userid project))
          projects))

(defn- remove-user-from-role! [id project userid role-key]
  (let [user-ids (set (map :userid (role-key project)))
        has-role? (contains? user-ids userid)]
    (if has-role?
      (do
        (projects/update-project!
         id
         (fn [project]
           (-> project
               (dissoc :project/invitations :project/applications)
               (update role-key #(filter (fn [u] (not= (:userid u) userid)) %)))))
        true)
      false)))

(defn- decline-accepted-project-invites! [id userid]
  (when-let [accepted-invites (seq (invitation/get-invitations-full {:project-id id :invited-user-id userid :accepted true}))]
    (doseq [invite accepted-invites]
      (invitation/leave-after-invitation! (:invitation/id invite)))))

(defn- owner-filter-match? [owner proj]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:project/owners proj))) owner)))

(defn- collaborator-filter-match? [collaborator proj]
  (or (nil? collaborator) ; return all when not specified
      (contains? (set (map :userid (:project/collaborators proj))) collaborator)))

(defn- owner-collaborator-union-filter? [owner collaborator project]
  (let [owner-ids (set (map :userid (:project/owners project)))
        collaborator-ids (set (map :userid (:project/collaborators project)))
        combined-ids (set/union owner-ids collaborator-ids)]
    (cond
      ;; Neither owner nor collaborator provided — return all
      (and (nil? owner) (nil? collaborator))
      true
      ;; Both provided — check if either is in combined set
      (and owner collaborator)
      (or (contains? combined-ids owner)
          (contains? combined-ids collaborator))
      ;; Only owner — check owners set
      owner
      (contains? owner-ids owner)
      ;; Only collaborator — check collaborators set
      collaborator
      (contains? collaborator-ids collaborator))))

(defn emails-are-unique? [new-invitations existing-emails]
  (if (not (nil? new-invitations))
    (let [new-emails (map (partial get-value-as-lower-case :email) new-invitations)
          all-emails (concat new-emails existing-emails)
          all-emails-count (count all-emails)
          unique-emails-count (count (set all-emails))]
      (= all-emails-count unique-emails-count))
    true))

(defn create-mass-invite [invites proj-id user-id]
  (doseq [invite invites]
        (invitation/create-invitation!
          (assoc invite
                :userid user-id
                :project-id proj-id))))

(defn link-project! [cmd]
  (let [proj-id (:project/id cmd)
        app-id (:application/id cmd)]
    (applications/get-application-for-user (getx-user-id) app-id) ;; throws forbidden, application membership
    (check-project-membership! cmd)
    (if-let [apid (projects/link-project! app-id proj-id)]
      {:success true
       :project-application/id apid}
      {:success false})))

(defn- project-filters [userid owner collaborator projects]
  (->> projects
       (apply-user-permissions userid)
       (filter (partial owner-collaborator-union-filter? owner collaborator))
       (doall)))

(defn get-projects [& [{:keys [userid owner collaborator enabled archived]}]]
  (->> (projects/get-projects)
       (db/apply-filters (assoc-some {}
                                     :enabled enabled
                                     :archived archived))
       (project-filters userid owner collaborator)))

(defn get-project [userid proj]
  (->> (get-projects {:userid userid})
       (find-first (comp #{(:project/id proj)} :project/id))))

(defn add-project! [userid proj]
  (let [user-email (.toLowerCase (getx-user-email))
        invites (get proj :project/invitations)
        applications (get proj :project/applications)
        proj-data (-> proj (dissoc :project/invitations :project/applications)
                           (assoc :project/owners [{:userid userid}]))]
    (if (emails-are-unique? invites [user-email])
      (if-let [id (projects/add-project! userid proj-data)]
        (do
          (create-mass-invite invites id userid)
          (doseq [application applications]
            (link-project! {:application/id (:id application) :project/id id}))
          {:success true
           :project/id id}))
      {:success false
       :errors [{:type :t.actions.errors/duplicate-email-addresses}]})))

(defn edit-project! [cmd]
  (let [proj-id (:project/id cmd)
        user-id (getx-user-id)
        project (projects/getx-project-by-id proj-id)
        new-invites (get cmd :project/new-invitations)
        invites-pending-removal (get cmd :project/revoke-invitations)
        new-applications (get cmd :project/new-applications)]
    (check-allowed-this-project! project)
    ;; todo - check if they CAN edit the project
    ;; todo - if the current user or any current owners or collaborators are included in the invitees list, raise an error
    {:success true
     :project/id 0}))
    ;; (rems.service.cadre.util/check-allowed-project! cmd)
    ;; ;; Check that the users who are being elevated & demoted are current members
    ;; (projects/update-project! id (fn [project] (->> (dissoc cmd :project/id)
    ;;                                                 (merge project))))
    ;; {:success true
    ;;  :project/id id}))



(defn set-project-enabled! [{:keys [enabled] :as cmd}]
  (let [id (:project/id cmd)]
    (check-allowed-project! cmd)
    (projects/update-project! id (fn [project] (assoc project :enabled enabled)))
    {:success true}))

(defn set-project-archived! [{:keys [archived] :as cmd}]
  (let [id (:project/id cmd)]
    (check-allowed-project! cmd)
    (or (dependencies/change-archive-status-error archived  {:project/id id})
        (do
          (projects/update-project! id (fn [project] (assoc project :archived archived)))
          {:success true}))))

(defn leave-project! [cmd]
  (let [userid (getx-user-id)
        id (:project/id cmd)
        project (projects/getx-project-by-id id)]
    (check-project-membership! cmd) ;; only project-owners & project-collaborator, not CADRE owners
    (if (< 1 (+ (count (:project/owners project))
                (count (:project/collaborators project)))) ;; don't let user leave if they're the last user.
      (do
        (decline-accepted-project-invites! id userid)
        {:success (or (remove-user-from-role! id project userid :project/owners)
                      (remove-user-from-role! id project userid :project/collaborators))})
      {:success false
       :errors [{:type :t.leave-project.errors/last-user-cannot-leave}]})))

(defn get-available-owners [] (users/get-users))

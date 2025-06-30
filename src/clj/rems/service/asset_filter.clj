(ns rems.service.asset-filter
  (:require [clojure.set :as set]
            [medley.core :refer [assoc-some find-first]]
            [rems.service.dependencies :as dependencies]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.workflow :as workflow]
            [rems.context :as context]
            [rems.util :refer [getx-user-id]]))

(defn- enrich-workflow-form [item]
  (-> item
      dependencies/enrich-dependency
      (select-keys [:form/id :form/internal-name :form/external-title])))

(defn- enrich-workflow-license [item]
  (-> item
      licenses/join-license
      organizations/join-organization))

(defn- join-dependencies [workflow]
  (when workflow
    (-> workflow
        organizations/join-organization
        (update-in [:workflow :forms] (partial map enrich-workflow-form))
        (update-in [:workflow :licenses] (partial map enrich-workflow-license)))))

(defn get-workflows [filters]
  (->> (workflow/get-workflows filters)
       (mapv join-dependencies)))

(defn- apply-user-permissions [userid organizations]
  (let [user-roles (set/union (roles/get-roles userid)
                              (organizations/get-all-organization-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (some? (some #{:owner :organization-owner :handler :reporter} user-roles))]
    (for [org organizations]
      (if (or (nil? userid) can-see-all?)
        org
        (dissoc org
                :organization/review-emails
                :organization/owners
                :enabled
                :archived)))))

(defn- owner-filter-match? [owner org]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:organization/owners org))) owner)))

(defn- organization-filters [userid owner organizations]
  (->> organizations
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (doall)))

(defn get-organizations [& [{:keys [userid owner enabled archived]}]]
  (->> (organizations/get-organizations)
       (db/apply-filters (assoc-some {}
                                     :enabled enabled
                                     :archived archived))
       (organization-filters userid owner)))

(defn get-organizations-by-id []
  (->> (organizations/get-organizations)
       (map (fn [org] [(get org :organization/id) org]))
       (into {})))

(defn may-view-organization-assets? [organization all-organization-handlers]
  (let [userid (getx-user-id)
        owner? (contains? context/*roles* :owner)
        organization-owners (set (map :userid (:organization/owners organization)))
        organization-owner? (contains? organization-owners userid)
        organization-handlers (get all-organization-handlers (:organization/id organization) {})
        organization-handler? (contains? organization-handlers userid)]
    (or owner?
        organization-owner?
        organization-handler?)))

(defn get-organization-handlers [filters]
  (let [workflows (get-workflows filters)]
    (->> workflows
         (map (fn [wf]
                [(get-in wf [:organization :organization/id])
                 (get-in wf [:workflow :handlers])]))
         (group-by first)
         (into {} (map (fn [[org-id pairs]]
                         [org-id (into {} (map (fn [user]
                                                 [(user :userid) user])
                                               (mapcat second pairs)))]))))))

(defn get-associated-assets
  [{:keys [get-asset-fn org-id-path filters]}]
  (let [assets (get-asset-fn filters)
        organizations (get-organizations-by-id)
        organization-handlers (get-organization-handlers {:enabled true
                                                                   :archived false})]
    (->> assets
         (filter (fn [asset]
                   (let [org-id (get-in asset org-id-path)
                         organization (get organizations org-id)]
                     (may-view-organization-assets? organization organization-handlers))))
         (vec))))

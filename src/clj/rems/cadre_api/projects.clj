(ns rems.cadre-api.projects
  (:require [compojure.api.sweet :refer :all]
            [rems.api.schema :as schema]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.schema-base :as schema-base]
            [rems.schema-base-cadre :as schema-base-cadre]
            [rems.service.cadre.projects :as projects]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(s/defschema CreateProjectCommand
  (-> schema-base-cadre/ProjectFull
      (dissoc :project/modifier
              :project/last-modifier)
      (assoc (s/optional-key :project/owners) [schema-base/User])))

(s/defschema CreateProjectResponse
  {:success s/Bool
   (s/optional-key :project/id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema EditProjectCommand CreateProjectCommand)

(s/defschema EditProjectResponse
  {:success s/Bool
   :project/id s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema ProjectEnabledCommand
  (merge schema-base-cadre/ProjectId
         {:enabled s/Bool}))

(s/defschema ProjectArchivedCommand
  (merge schema-base-cadre/ProjectId
         {:archived s/Bool}))

;; TODO: deduplicate or decouple with /api/applications/reviewers API?
(s/defschema AvailableOwner schema-base-cadre/UserWithAttributesCadre)
(s/defschema AvailableOwners [AvailableOwner])

(def projects-api
  (context "/cadre-projects" []
    :tags ["CADRE Projects"]

    (GET "/" []
      :summary "Get projects. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :query-params [{owner :- (describe s/Str "return only projects that are owned by owner") nil}
                     {disabled :- (describe s/Bool "whether to include disabled projects") false}
                     {archived :- (describe s/Bool "whether to include archived projects") false}]
      :return [schema-base-cadre/ProjectFull]
      (ok (projects/get-projects (merge {:userid (getx-user-id)
                                         :owner owner}
                                        (when-not disabled {:enabled true})
                                        (when-not archived {:archived false})))))

    (POST "/create" []
      :summary "Create project"
      :roles #{:owner}
      :body [command CreateProjectCommand]
      :return CreateProjectResponse
      (ok (projects/add-project! command)))

    (PUT "/edit" []
      :summary "Edit project. Project owners cannot change the owners."
      ;; explicit roles seem clearer here instead of +admin-write-roles+
      :roles #{:owner :project-owner}
      :body [command EditProjectCommand]
      :return EditProjectResponse
      (ok (projects/edit-project! command)))

    (PUT "/archived" []
      :summary "Archive or unarchive the project"
      :roles #{:owner}
      :body [command ProjectArchivedCommand]
      :return schema/SuccessResponse
      (ok (projects/set-project-archived! command)))

    (PUT "/enabled" []
      :summary "Enable or disable the project"
      :roles #{:owner}
      :body [command ProjectEnabledCommand]
      :return schema/SuccessResponse
      (ok (projects/set-project-enabled! command)))

    (GET "/available-owners" []
      :summary "List of available owners"
      :roles #{:owner :project-owner}
      :return AvailableOwners
      (ok (projects/get-available-owners)))

    (GET "/:project-id" []
      :summary "Get an project. Returns more information for owners and handlers."
      :roles #{:logged-in}
      :path-params [project-id :- (describe s/Int "project id")]
      :return schema-base-cadre/ProjectFull
      (if-let [org (projects/get-project (getx-user-id) {:project/id project-id})]
        (ok org)
        (not-found-json-response)))))

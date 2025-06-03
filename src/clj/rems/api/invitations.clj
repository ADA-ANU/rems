(ns rems.api.invitations
  (:require [compojure.api.sweet :refer :all]
            [rems.service.invitation :as invitation]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id getx-user-email]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CreateInvitationCommand
  {:name s/Str
   :email s/Str
   (s/optional-key :workflow-id) s/Int
   (s/optional-key :project-id) s/Int
   (s/optional-key :role) (describe s/Str "Will add as collaborator by default. Set to 'owner' if you wish to invite as owner")})

(s/defschema CreateInvitationResponse
  {:success s/Bool
   (s/optional-key :invitation/id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema AcceptInvitationResponse
  {:success s/Bool
   (s/optional-key :errors) [s/Any]
   (s/optional-key :invitation/workflow) {:workflow/id s/Int}
   (s/optional-key :invitation/project) {:project/id s/Int}})

(def invitations-api
  (context "/invitations" []
    :tags ["invitations"]

    (GET "/" []
      :summary "Get invitations"
      :roles +admin-read-roles+
      :query-params [{sent :- (describe s/Bool "whether to include sent invitations") nil}
                     {accepted :- (describe s/Bool "whether to include accepted invitations") nil}
                     {revoked :- (describe s/Bool "whether to include revoked invitations") nil}]
      :return [schema-base/InvitationResponse]
      (ok (invitation/get-invitations (merge {:userid (getx-user-id)}
                                             (when (some? sent) {:sent sent})
                                             (when (some? accepted) {:accepted accepted}
                                                   (when (some? revoked) {:revoked revoked}))))))

    (GET "/my-invitations" []
      :summary "Get my invitations"
      :roles #{:logged-in}
      :return [schema-base/InvitationResponse]
      (ok (invitation/get-my-invitations {:userid (getx-user-id)})))

    (POST "/create" []
      :summary "Create an invitation. The invitation will be sent asynchronously to the recipient."
      :roles #{:logged-in}
      :body [command CreateInvitationCommand]
      :return CreateInvitationResponse
      (ok (invitation/create-invitation! (assoc command :userid (getx-user-id)))))

    (POST "/revoke" []
      :summary "Revoke an invitation. The revocation will be sent asynchronously to the recipient."
      :roles #{:logged-in}
      :query-params [{id :- (describe s/Int "id of invitation") false}]
      :return AcceptInvitationResponse
      (ok (invitation/revoke-invitation! {:userid (getx-user-id) :id id})))

    (POST "/decline-invitation" []
      :summary "Decline an invitation. The invitation token will be spent."
      :roles #{:logged-in}
      :query-params [{token :- (describe s/Str "secret token of the invitation") false}]
      :return AcceptInvitationResponse
      (ok (invitation/decline-invitation! {:userid (getx-user-id) :email (getx-user-email) :token token})))

    (POST "/accept-invitation" []
      :summary "Accept an invitation. The invitation token will be spent."
      :roles #{:logged-in}
      :query-params [{token :- (describe s/Str "secret token of the invitation") false}]
      :return AcceptInvitationResponse
      (ok (invitation/accept-invitation! {:userid (getx-user-id) :token token})))))

(ns rems.api.applications
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [compojure.api.sweet :refer :all]
            [rems.api.applications-v2 :as applications-v2]
            [rems.api.schema :refer :all]
            [rems.api.util]
            [rems.application.commands :as commands]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.users :as users]
            [rems.pdf :as pdf]
            [rems.util :refer [getx-user-id update-present]]
            [ring.middleware.multipart-params :as multipart]
            [ring.swagger.upload :as upload]
            [ring.util.http-response :refer :all]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import [java.io ByteArrayInputStream]))

;; Response models

(s/defschema CreateApplicationCommand
  {:catalogue-item-ids [s/Int]})

(s/defschema CreateApplicationResponse
  {:success s/Bool
   (s/optional-key :application-id) s/Int})

(s/defschema User
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Applicant
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Commenter
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Commenters
  [Commenter])

(s/defschema Decider
  {:userid s/Str
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)})

(s/defschema Deciders
  [Decider])

(s/defschema Command
  {:type s/Keyword
   s/Any s/Any})

(s/defschema AcceptInvitationResult
  {:success s/Bool
   (s/optional-key :application-id) s/Num
   (s/optional-key :errors) [s/Any]})

;; Api implementation

(defn invalid-user? [u]
  (or (str/blank? (:eppn u))
      (str/blank? (:commonName u))
      (str/blank? (:mail u))))

(defn format-user [u]
  {:userid (:eppn u)
   :name (:commonName u)
   :email (:mail u)})

;; TODO Filter applicant, requesting user
(defn get-users []
  (->> (users/get-all-users)
       (remove invalid-user?)
       (map format-user)))

(def get-applicants get-users)

(def get-commenters get-users)

(def get-deciders get-users)

(defn- fix-command-from-api
  [cmd]
  ;; schema could do these coercions for us...
  (update-present cmd :decision keyword))

(def applications-api
  (context "/applications" []
    :tags ["applications"]

    (GET "/commenters" []
      :summary "Available third party commenters"
      :roles #{:handler}
      :return Commenters
      (ok (get-commenters)))

    (GET "/members" []
      :summary "Existing REMS users available for application membership"
      :roles #{:handler}
      :return [Applicant]
      (ok (get-applicants)))

    (GET "/deciders" []
      :summary "Available deciders"
      :roles #{:handler}
      :return Deciders
      (ok (get-deciders)))

    (GET "/attachments" []
      :summary "Get an attachment for a field in an application"
      :roles #{:logged-in}
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      (if-let [attachment (attachments/get-attachment (getx-user-id) application-id field-id)]
        (-> (ok (:data attachment))
            (content-type (:content-type attachment)))
        (not-found! "not found")))

    (POST "/accept-invitation" []
      :summary "Accept an invitation by token"
      :roles #{:logged-in}
      :query-params [invitation-token :- (describe s/Str "invitation token")]
      :return AcceptInvitationResult
      (ok (applications/accept-invitation (getx-user-id) invitation-token)))

    (GET "/:application-id/pdf" []
      :summary "Get a pdf version of an application"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Num "application id")]
      :produces ["application/pdf"]
      (if-let [app (applications-v2/get-application (getx-user-id) application-id)]
        (-> app
            (pdf/application-to-pdf-bytes)
            (ByteArrayInputStream.)
            (ok)
            (content-type "application/pdf"))
        (not-found! "not found")))

    ;; TODO: think about size limit
    (POST "/add-attachment" []
      :summary "Add an attachment file related to an application field"
      :roles #{:applicant}
      :multipart-params [file :- upload/TempFileUpload]
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      :middleware [multipart/wrap-multipart-params]
      :return SuccessResponse
      (attachments/save-attachment! file (getx-user-id) application-id field-id)
      (ok {:success true}))

    (POST "/remove-attachment" []
      :summary "Remove an attachment file related to an application field"
      :roles #{:applicant}
      :query-params [application-id :- (describe s/Int "application id")
                     field-id :- (describe s/Int "application form field id the attachment is related to")]
      :return SuccessResponse
      (attachments/remove-attachment! (getx-user-id) application-id field-id)
      (ok {:success true}))))

(defn api-command [command-type request]
  (let [command (-> request
                    (assoc :type command-type)
                    (assoc :actor (getx-user-id))
                    (assoc :time (time/now))
                    (fix-command-from-api))
        errors (applications/command! command)]
    (if errors
      (ok {:success false
           :errors (:errors errors)})
      (ok {:success true}))))

(defmacro command-endpoint [command schema]
  (let [path (str "/" (name command))]
    `(POST ~path []
       :summary ~(str "Submit a `" (name command) "` command for an application.")
       :roles #{:logged-in}
       :body [request# ~schema]
       :return SuccessResponse
       (api-command ~command request#))))

(def application-commands-api
  (context "/applications" []
    :tags ["applications"]
    (command-endpoint :application.command/accept-invitation commands/AcceptInvitationCommand)
    (command-endpoint :application.command/add-member commands/AddMemberCommand)
    (command-endpoint :application.command/invite-member commands/InviteMemberCommand)
    (command-endpoint :application.command/approve commands/ApproveCommand)
    (command-endpoint :application.command/close commands/CloseCommand)
    (command-endpoint :application.command/comment commands/CommentCommand)
    (command-endpoint :application.command/decide commands/DecideCommand)
    (command-endpoint :application.command/reject commands/RejectCommand)
    (command-endpoint :application.command/request-comment commands/RequestCommentCommand)
    (command-endpoint :application.command/request-decision commands/RequestDecisionCommand)
    (command-endpoint :application.command/remove-member commands/RemoveMemberCommand)
    (command-endpoint :application.command/return commands/ReturnCommand)
    (command-endpoint :application.command/save-draft commands/SaveDraftCommand)
    (command-endpoint :application.command/submit commands/SubmitCommand)
    (command-endpoint :application.command/uninvite-member commands/UninviteMemberCommand)))

(def v2-applications-api
  (context "/v2/applications" []
    :tags ["applications"]

    (GET "/" []
      :summary "Get current user's all applications"
      :roles #{:logged-in}
      :return [ApplicationOverview]
      (ok (applications-v2/get-own-applications (getx-user-id))))

    (POST "/create" []
      :summary "Create a new application"
      :roles #{:logged-in}
      :body [request CreateApplicationCommand]
      :return CreateApplicationResponse
      (ok (applications/create-application! (getx-user-id) (:catalogue-item-ids request))))

    (GET "/:application-id" []
      :summary "Get application by `application-id`"
      :roles #{:logged-in}
      :path-params [application-id :- (describe s/Num "application id")]
      :responses {200 {:schema Application}
                  404 {:schema s/Str :description "Not found"}}
      (if-let [app (applications-v2/get-application (getx-user-id) application-id)]
        (ok app)
        (not-found! "not found")))))

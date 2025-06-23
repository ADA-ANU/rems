(ns rems.cadre-api.cannedresponses
  (:require [compojure.api.sweet :refer :all]
            [rems.db.cadredb.cannedresponses :as cannedresponses]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CreateCannedResponseCommand
  {:orgid s/Str
   :response s/Str
   :title s/Str
   (s/optional-key :tags) [s/Int]})

(s/defschema CannedResponseResponse
  {:success s/Bool
   (s/optional-key :id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema CreateCannedResponseMapping
  {:success s/Bool
   :tag s/Int})

(s/defschema ToggleCannedResponseState
  {:id s/Int
   :enabled s/Bool})

(s/defschema CannedResponseAndMappingResponse
  (merge CannedResponseResponse
         {(s/optional-key :mapping) [CreateCannedResponseMapping]}))

(s/defschema CannedResponseDataResponse
  {:success s/Bool
   (s/optional-key :cannedresponses) [cannedresponses/CannedResponse]
   (s/optional-key :errors) [s/Any]})

(s/defschema CannedResponseTagDataResponse
  {:success s/Bool
   (s/optional-key :cannedresponsetags) [cannedresponses/CannedResponseTag]
   (s/optional-key :errors) [s/Any]})

(s/defschema CannedResponseMappingDataResponse
  {:success s/Bool
   (s/optional-key :cannedresponsemapping) [cannedresponses/CannedResponseMapping]
   (s/optional-key :errors) [s/Any]})


(def cannedresponses-api
  (context "/cannedresponses" []
    :tags ["cannedresponses"]

    (GET "/" []
      :summary "Get canned responses"
      :roles #{:owner}
      :query-params [{appid :- (describe s/Int "Limit to canned responses for this application id") nil}
                     {orgid :- (describe s/Str "Limit to canned responses for this organisation id") nil}
                     {tagid :- (describe s/Int "Limit to canned responses for this tag id") nil}
                     {id :- (describe s/Int "Limit to this canned response id") nil}
                     {enabled :- (describe s/Bool "Limit to canned responses enabled or disabled") nil}]
      :return CannedResponseDataResponse
      (ok (cannedresponses/get-cannedresponses (merge     (when (some? appid) {:appid appid})
                                                          (when (some? enabled) {:enabled enabled})
                                                          (when (some? orgid) {:orgid orgid})
                                                          (when (some? tagid) {:tagid tagid})
                                                          (when (some? id) {:id id})))))

    (GET "/tag" []
      :summary "Get canned response tags"
      :roles #{:owner}
      :query-params [{appid :- (describe s/Int "Limit to canned response tags for this application id") nil}
                     {orgid :- (describe s/Str "Limit to canned response tags for this organisation id") nil}
                     {id :- (describe s/Int "Limit to this canned response tag id") nil}
                     {enabled :- (describe s/Bool "Limit to canned response tags enabled or disabled") nil}]
      :return CannedResponseTagDataResponse
      (ok (cannedresponses/get-cannedresponse-tags (merge     (when (some? appid) {:appid appid})
                                                              (when (some? enabled) {:enabled enabled})
                                                              (when (some? orgid) {:orgid orgid})
                                                              (when (some? id) {:id id})))))


    (GET "/get-by-appid" []
      :summary "Get cannedresponses related to this appid"
      :roles #{:handler}
      :query-params [{appid :- (describe s/Int "Application id") false}]
      :return CannedResponseDataResponse
      (ok (cannedresponses/get-app-cannedresponses appid (getx-user-id))))

    (GET "/mapping" []
      :summary "Get cannedresponse mapping with tags"
      :roles #{:owner}
      :query-params [{tagid :- (describe s/Int "Limit by canned response tag id") nil}
                     {responseid :- (describe s/Int "Limit by canned response id") nil}]
      :return CannedResponseMappingDataResponse
      (ok (cannedresponses/get-cannedresponse-mapping {:tagid tagid :responseid responseid})))

    (POST "/" []
      :summary "Create a canned response"
      :roles #{:owner :organization-owner}
      :body [command CreateCannedResponseCommand]
      :return CannedResponseAndMappingResponse
      (ok (cannedresponses/create-cannedresponse! command)))

    (POST "/mapping" []
      :summary "Create a canned response mapping between a tag and a response"
      :roles #{:owner :organization-owner}
      :query-params [{tagid :- (describe s/Int "Tag id") false}
                     {responseid :- (describe s/Int "Canned response id") false}]
      :return CannedResponseResponse
      (ok (cannedresponses/create-cannedresponse-mapping! {:tagid tagid :responseid responseid})))

    (POST "/tag" []
      :summary "Create a canned response tag"
      :roles #{:owner :organization-owner}
      :query-params [{orgid :- (describe s/Str "Organisation id") false}
                     {tag :- (describe s/Str "Tag text") false}]
      :return CannedResponseResponse
      (ok (cannedresponses/create-tag! {:orgid orgid :tag tag})))

    (PUT "/enabled" []
      :summary "Enable or disable the canned response"
      :roles #{:owner}
      :body [command ToggleCannedResponseState]
      :return CannedResponseResponse
      (ok (cannedresponses/set-cannedresponse-enabled! command)))

    (PUT "/tag/enabled" []
      :summary "Enable or disable the canned response tag"
      :roles #{:owner}
      :body [command ToggleCannedResponseState]
      :return CannedResponseResponse
      (ok (cannedresponses/set-cannedresponse-tag-enabled! command)))


    (DELETE "/mapping" []
      :summary "Delete a canned response mapping between a tag and a response"
      :roles #{:owner}
      :query-params [{id :- (describe s/Int "Canned response mapping id") false}]
      :body [command CreateCannedResponseCommand]
      :return CannedResponseResponse
      (ok (cannedresponses/delete-cannedresponse-mapping! {:id id})))))

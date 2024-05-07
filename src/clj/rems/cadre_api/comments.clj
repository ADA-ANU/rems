(ns rems.cadre-api.comments
  (:require [compojure.api.sweet :refer :all]
            [rems.db.cadredb.comments :as comments]
            [rems.api.util :refer [not-found-json-response]] ; required for route :roles
            [rems.common.roles :refer [+admin-read-roles+ +admin-write-roles+]]
            [rems.schema-base :as schema-base]
            [rems.util :refer [getx-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(s/defschema CreateCommentCommand
  {(s/optional-key :appid) s/Int
   (s/optional-key :useridto) s/Str
   :commenttext s/Str})

(s/defschema CommentResponse
  {:success s/Bool
   (s/optional-key :comment/id) s/Int
   (s/optional-key :errors) [s/Any]})

(s/defschema CommentDataResponse
  {:success s/Bool
   (s/optional-key :comments) [comments/Comment]
   (s/optional-key :errors) [s/Any]})


(def comments-api
  (context "/comments" []
    :tags ["comments"]

    (GET "/" []
      :summary "Get comments"
      :roles +admin-read-roles+
      :query-params [{addressed_to :- (describe s/Str "Comments addressed to this user") nil}
                     {created_by :- (describe s/Str "Comments created by this user") nil}
                     {userid :- (describe s/Str "Comments created by OR addressed to this user") nil}
                     {appid :- (describe s/Int "Comments related to this application id") nil}
                     {markedread :- (describe s/Bool "whether to include comments marked as read") nil}]
      :return CommentDataResponse
      (ok (comments/get-comments (merge     (when (some? userid) {:useridt userid})
                                            (when (some? userid) {:useridf userid})
                                            (when (some? markedread) {:markedread markedread})
                                            (when (some? addressed_to) {:addressed_to addressed_to})
                                            (when (some? created_by) {:created_by created_by})
                                            (when (some? appid) {:appid appid})))))

    (GET "/get-comments-by-appid" []
      :summary "Get comments related to me and this appid"
      :roles #{:logged-in}
      :query-params [{appid :- (describe s/Int "Application id") false}]
      :return CommentDataResponse
      (ok (comments/get-comments {:useridf (getx-user-id) :useridt (getx-user-id) :appid appid})))

    (GET "/get-my-comments" []
      :summary "Get comments related to me (to and from)"
      :roles #{:logged-in}
      :return CommentDataResponse
      (ok (comments/get-comments {:useridt (getx-user-id) :useridf (getx-user-id)})))

    (POST "/create" []
      :summary "Create a comment"
      :roles #{:logged-in}
      :body [command CreateCommentCommand]
      :return CommentResponse
      (ok (comments/create-comment! (assoc command :userid (getx-user-id)))))

    (POST "/markread-comment" []
      :summary "Mark a comment as read"
      :roles #{:logged-in}
      :query-params [{id :- (describe s/Int "Comment id") false}]
      :return CommentResponse
      (ok (comments/markread-comment! {:addressed_to (getx-user-id) :id id})))))

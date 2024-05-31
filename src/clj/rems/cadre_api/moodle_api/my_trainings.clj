(ns rems.cadre-api.moodle-api.my-trainings
  (:require [compojure.api.sweet :refer :all]
            [rems.service.cadre.moodle :as moodle]
            [rems.api.util] ; required for route :roles
            [rems.util :refer [getx get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

;; API Request Models
(s/defschema CourseCompletionStatusRequestBody
  {:course-id s/Str})

(def my-trainings-api
  (context "/my-trainings" []
    :tags ["mytrainings"]

    (GET "/get-user-details" request
      :summary "Fetches the details of the current logged-in user from the CADRE Moodle training App."
      :roles #{:logged-in}
      :return s/Any
      (ok (moodle/get-moodle-user (get-user-id))))

    (GET "/get-list-of-all-courses" request
      :summary "Fetches the list of all visible and non-visible courses in CADRE Moodle Training Web App."
      :roles #{:logged-in}
      :return s/Any
      (ok (moodle/get-moodle-courselist)))

    (GET "/get-course-completion-status" request
      :summary "Get the CADRE Moodle course completion status of a user for a specific course."
      :query-params [{course-id :- (describe s/Str "Input the CADRE Moodle App courseID for which the course completion status needs to be checked.") nil}]
      :roles #{:logged-in}
      :return s/Any
      (ok (moodle/get-moodle-course-completed course-id (moodle/get-moodle-user (get-user-id)))))

    (POST "/get-course-completion-status" request
      :summary "Get the CADRE Moodle course completion status of a user for a specific course."
      :body [req-body CourseCompletionStatusRequestBody]
      :roles #{:logged-in}
      :return s/Any
      (ok (moodle/get-moodle-course-completed (:course-id CourseCompletionStatusRequestBody) (moodle/get-moodle-user (get-user-id)))))

    (GET "/get-all-enrolled-users-in-course" request
      :summary "This API get list of all users enrolled in a particular CADRE Moodle course."
      :query-params [{course-id :- (describe s/Str "Input the CADRE Moodle App courseID for which the enrolled users needs to be fetched.") nil}]
      :roles #{:owner :organization-owner :reporter :handler}
      :return s/Any
      (ok (moodle/get-moodle-all-enrolled-users course-id)))

    (GET "/get-logged-in-user-enrolled-courses" request
      :summary "This API gets list of all enrolled courses of logged-in user."
      :roles #{:logged-in}
      :return s/Any
      (ok (moodle/get-moodle-enrolled-courses (moodle/get-moodle-user (get-user-id)))))))

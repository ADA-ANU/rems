(ns rems.cadre-api.moodle-api.my-trainings
  (:require [compojure.api.sweet :refer :all]
            [clj-http.client :as client]
            [rems.api.util] ; required for route :roles
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [rems.util :refer [getx get-user-id]]
            [cheshire.core :as cheshire-json]
            [rems.json :as json]
            [schema.core :as s]))

(def my-trainings-api
  (context "/my-trainings" []
    :tags ["mytrainings"]

    (GET "/get-user-details" request
      :summary "Fetches the details of the current logged-in user from the CADRE Moodle training App."
      :roles #{:logged-in}

      (let [user-id (get-user-id)
            cookies (:cookies request)
            cadre-moodle-app-userid (get cookies "cadre-moodle-app-userid")]

        (when (:log-authentication-details env)
          (log/info "user-id === " user-id)
          (log/info "cadre-moodle-app-userid === " cadre-moodle-app-userid))

        (when user-id
          (try
            (let [cadre-moodle-app-wsfunction "core_user_get_users"
                  url (str (getx env :cadre-moodle-app-api-url)
                           "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                           "&wsfunction=" cadre-moodle-app-wsfunction
                           "&moodlewsrestformat=json"
                           "&criteria[0][key]=username"
                           "&criteria[0][value]=" user-id)
                  response (client/get url {:accept :json})]

              (when (:log-authentication-details env)
                (log/info "url == " url)
                (log/info "response - status == " (:status response))
                (log/info "response - Headers == " (:headers response))
                (log/info "response - Body == " (:body response))
                (log/info "json/parse-string of body == " (json/parse-string (:body response)))
                (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))

              (if (= 200 (:status response))
                (let [parsed-json (json/parse-string (:body response))
                      users (:users parsed-json)
                      first-user (first users)
                      id (:id first-user)]
                  (log/info "parsed-json == " parsed-json)
                  (log/info "users == " users)
                  (log/info "first-user == " first-user)
                 (log/info "id == " id) 
                 (-> {:status  200
                      :headers {"Content-Type" "application/json"}
                      :body (cheshire-json/generate-string (json/parse-string (:body response)))}
                     (set-cookie "cadre-moodle-app-user-id" id {:http-only true})))
                (throw (ex-info "Non-200 status code returned: " {:response response}))))
            (catch Exception e
              (log/error "Error invoking Moodle API - " "core_user_get_users :" (.getMessage e)))))))

    (GET "/get-list-of-all-courses" request
      :summary "Fetches the list of all visible and non-visible courses in CADRE Moodle Training Web App."
      :roles #{:logged-in}
      (try
        (let [cadre-moodle-app-wsfunction "core_course_get_courses"
              url (str (getx env :cadre-moodle-app-api-url)
                       "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                       "&wsfunction=" cadre-moodle-app-wsfunction
                       "&moodlewsrestformat=json")
              response (client/get url {:accept :json})]
          (when (:log-authentication-details env)
            (log/info "url == " url)
            (log/info "response - status == " (:status response))
            (log/info "response - Headers == " (:headers response))
            (log/info "response - Body == " (:body response))
            (log/info "json/parse-string of body == " (json/parse-string (:body response)))
            (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))

          (if (= 200 (:status response))
            {:status  200
             :headers {"Content-Type" "application/json"}
             :body (cheshire-json/generate-string (json/parse-string (:body response)))}
            (throw (ex-info "Non-200 status code returned: " {:response response}))))

        (catch Exception e
          (log/error "Error invoking Moodle API - " "core_course_get_courses :" (.getMessage e)))))

    (GET "/get-course-completion-status" request
      :summary "Get the CADRE Moodle course completion status of a user for a specific course."
      :query-params [{course-id :- (describe s/Str "Input the CADDRE Moodle App courseID for which the course completion status needs to be checked.") nil}]
      :roles #{:logged-in}

      (let [user-id (get-user-id)
            cookies (:cookies request)
            cadre-moodle-app-user-id (:value (get cookies "cadre-moodle-app-user-id"))]

        (when (:log-authentication-details env)
          (log/info "CADRE userid == " user-id)
          (log/info "Moodle userid == " cadre-moodle-app-user-id)
          (log/info "Course ID == " course-id))

        (when user-id
          (try
            (let [cadre-moodle-app-wsfunction "core_completion_get_course_completion_status"
                  url (str (getx env :cadre-moodle-app-api-url)
                           "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                           "&wsfunction=" cadre-moodle-app-wsfunction
                           "&moodlewsrestformat=json"
                           "&courseid=" course-id
                           "&userid=" cadre-moodle-app-user-id)
                  response (client/get url {:accept :json})]

              (when (:log-authentication-details env)
                (log/info "url == " url)
                (log/info "response - status == " (:status response))
                (log/info "response - Headers == " (:headers response))
                (log/info "response - Body == " (:body response))
                (log/info "json/parse-string of body == " (json/parse-string (:body response)))
                (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))

              (if (= 200 (:status response))
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body (cheshire-json/generate-string (json/parse-string (:body response)))}
                (throw (ex-info "Non-200 status code returned: " {:response response}))))
            (catch Exception e
              (log/error "Error invoking Moodle API - " "core_completion_get_course_completion_status :" (.getMessage e)))))))

    (GET "/get-all-enrolled-users-in-course" request
      :summary "This API get list of all users enrolled in a particular CADRE Moodle course."
      :query-params [{course-id :- (describe s/Str "Input the CADDRE Moodle App courseID for which the enrolled users needs to be fetched.") nil}]
      :roles #{:owner :organization-owner :reporter :handler}

      (let [user-id (get-user-id)]

        (when (:log-authentication-details env)
          (log/info "CADRE userid == " user-id)
          (log/info "Course ID == " course-id))

        (when user-id
          (try
            (let [cadre-moodle-app-wsfunction "core_enrol_get_enrolled_users"
                  url (str (getx env :cadre-moodle-app-api-url)
                           "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                           "&wsfunction=" cadre-moodle-app-wsfunction
                           "&moodlewsrestformat=json"
                           "&courseid=" course-id)
                  response (client/get url {:accept :json})]

              (when (:log-authentication-details env)
                (log/info "url == " url)
                (log/info "response - status == " (:status response))
                (log/info "response - Headers == " (:headers response))
                (log/info "response - Body == " (:body response))
                (log/info "json/parse-string of body == " (json/parse-string (:body response)))
                (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))

              (if (= 200 (:status response))
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body (cheshire-json/generate-string (json/parse-string (:body response)))}
                (throw (ex-info "Non-200 status code returned: " {:response response}))))
            (catch Exception e
              (log/error "Error invoking Moodle API - " "core_enrol_get_enrolled_users :" (.getMessage e)))))))

      (GET "/get-logged-in-user-enrolled-courses" request
        :summary "This API gets list of all enrolled courses of logged-in user."
        :roles #{:logged-in}

        (let [user-id (get-user-id)
              cookies (:cookies request)
              cadre-moodle-app-user-id (:value (get cookies "cadre-moodle-app-user-id"))]

          (when (:log-authentication-details env)
            (log/info "CADRE userid == " user-id)
            (log/info "Moodle userid == " cadre-moodle-app-user-id))

          (when user-id
            (try
              (let [cadre-moodle-app-wsfunction "core_enrol_get_users_courses"
                    url (str (getx env :cadre-moodle-app-api-url)
                             "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                             "&wsfunction=" cadre-moodle-app-wsfunction
                             "&moodlewsrestformat=json"
                             "&userid=" cadre-moodle-app-user-id)
                    response (client/get url {:accept :json})]

                (when (:log-authentication-details env)
                  (log/info "url == " url)
                  (log/info "response - status == " (:status response))
                  (log/info "response - Headers == " (:headers response))
                  (log/info "response - Body == " (:body response))
                  (log/info "json/parse-string of body == " (json/parse-string (:body response)))
                  (log/info "cheshire-json/generate-string of json/parse-string == " (cheshire-json/generate-string (json/parse-string (:body response)))))

                (if (= 200 (:status response))
                  {:status  200
                   :headers {"Content-Type" "application/json"}
                   :body (cheshire-json/generate-string (json/parse-string (:body response)))}
                  (throw (ex-info "Non-200 status code returned: " {:response response}))))
              (catch Exception e
                (log/error "Error invoking Moodle API - " "core_enrol_get_users_courses :" (.getMessage e)))))))))
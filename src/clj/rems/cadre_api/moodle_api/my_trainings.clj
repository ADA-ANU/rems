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

      (let [user-id (get-user-id)]

        (when (:log-authentication-details env)
          (log/info "user-id === " user-id))

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
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body (cheshire-json/generate-string (json/parse-string (:body response)))}
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

    (GET "/get-user-details" request
      :summary "Fetches the details of the current logged-in user from the CADRE Moodle training App."
      :roles #{:logged-in}

      (let [user-id (get-user-id)]

        (when (:log-authentication-details env)
          (log/info "user-id === " user-id))

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
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body (cheshire-json/generate-string (json/parse-string (:body response)))}
                (throw (ex-info "Non-200 status code returned: " {:response response}))))

            (catch Exception e
              (log/error "Error invoking åMoodle API - " "core_user_get_users :" (.getMessage e)))))))

    (GET "/get_course_completion_status" request
      :summary "Get the CADRE Moodle course completion status of a user for a specific course."
      :query-params [{moodle-userid :- (describe s/Str "Input the CADRE Moodle App UserID.") nil}
                     {course-id :- (describe s/Str "Input the CADDRE Moodle App courseID for which the course completion status needs to be checked.") nil}]
      :roles #{:logged-in}

      (let [user-id (get-user-id)]

        (when (:log-authentication-details env)
          (log/info "CADRE userid == " user-id)
          (log/info "Moodle userid == " moodle-userid)
          (log/info "Course ID == " course-id))

        (when user-id
          (try
            (let [cadre-moodle-app-wsfunction "core_completion_get_course_completion_status"
                  url (str (getx env :cadre-moodle-app-api-url)
                           "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                           "&wsfunction=" cadre-moodle-app-wsfunction
                           "&moodlewsrestformat=json"
                           "&courseid=" course-id
                           "&userid=" moodle-userid)
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
              (log/error "Error invoking Moodle API - " "core_completion_get_course_completion_status :" (.getMessage e)))))))))
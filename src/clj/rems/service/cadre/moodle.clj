(ns rems.service.cadre.moodle
  (:require [clj-http.client :as client]
            [rems.config :refer [env]]
            [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [rems.json :as json]
            [rems.util :refer [getx]]
            [schema.core :as s]))

(defn get-moodle-user [user-id]
  (try
    (let [cadre-moodle-app-wsfunction "core_user_get_users"
          url (str (getx env :cadre-moodle-app-api-url)
                   "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                   "&wsfunction=" cadre-moodle-app-wsfunction
                   "&moodlewsrestformat=json"
                   "&criteria[0][key]=username"
                   "&criteria[0][value]=" user-id)
          response (client/get url {:accept :json})]

      (if (= 200 (:status response))
        (let [parsed-json (json/parse-string (:body response))
              users (:users parsed-json)
              first-user (first users)
              id (:id first-user)]
          id)
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking Moodle API - " "core_user_get_users :" (.getMessage e)))))

(defn get-moodle-courselist []
  (try
    (let [cadre-moodle-app-wsfunction "core_course_get_courses"
          url (str (getx env :cadre-moodle-app-api-url)
                   "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                   "&wsfunction=" cadre-moodle-app-wsfunction
                   "&moodlewsrestformat=json")
          response (client/get url {:accept :json})]

      (if (= 200 (:status response))
        (json/parse-string (:body response))
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking Moodle API - " "core_course_get_courses :" (.getMessage e)))))


(defn get-moodle-course-completed [course-id moodle-user-id]
  (try
    (let [cadre-moodle-app-wsfunction "core_completion_get_course_completion_status"
          url (str (getx env :cadre-moodle-app-api-url)
                   "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                   "&wsfunction=" cadre-moodle-app-wsfunction
                   "&moodlewsrestformat=json"
                   "&courseid=" course-id
                   "&userid=" moodle-user-id)
          response (client/get url {:accept :json})]
      (if (= 200 (:status response))
        (json/parse-string (:body response))
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking Moodle API - " "core_completion_get_course_completion_status :" (.getMessage e)))))

(defn get-moodle-all-enrolled-users [course-id]
  (try
    (let [cadre-moodle-app-wsfunction "core_enrol_get_enrolled_users"
          url (str (getx env :cadre-moodle-app-api-url)
                   "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                   "&wsfunction=" cadre-moodle-app-wsfunction
                   "&moodlewsrestformat=json"
                   "&courseid=" course-id)
          response (client/get url {:accept :json})]

      (if (= 200 (:status response))
        (json/parse-string (:body response))
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking Moodle API - " "core_enrol_get_enrolled_users :" (.getMessage e)))))

(defn get-moodle-enrolled-courses [moodle-user-id]
  (try
    (let [cadre-moodle-app-wsfunction "core_enrol_get_users_courses"
          url (str (getx env :cadre-moodle-app-api-url)
                   "?wstoken=" (getx env :cadre-moodle-app-wstoken)
                   "&wsfunction=" cadre-moodle-app-wsfunction
                   "&moodlewsrestformat=json"
                   "&userid=" moodle-user-id)
          response (client/get url {:accept :json})]
      (if (= 200 (:status response))
        (json/parse-string (:body response))
        (throw (ex-info "Non-200 status code returned: " {:response response}))))
    (catch Exception e
      (log/error "Error invoking Moodle API - " "core_enrol_get_users_courses :" (.getMessage e)))))

(ns rems.db.cadredb.users
  (:require [clojure.test :refer :all]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.service.cadre.moodle :as moodle]
            [clojure.tools.logging :as log]
            [cheshire.core :as cheshire-json]))

;; Append new key-value pair to the map
(defn append-remaining-key-value-pairs-to-json [existing-map dsr_count dsa_count projects_count external_training_count training_count]
  (assoc existing-map :number_projects projects_count :number_dsrs dsr_count :number_dsas dsa_count :number_external_trainings external_training_count :number_trainings training_count))

(defn fetch-user-profile
  "fetch dashboard - user profile page"
  [userid]
  ;;(assert userid "userid cannot be NULL")
  (log/info "userid == " userid)
  (let [userattrs-json (:userattrs (db/get-user-profile {:userid userid}))
        dsr_count (:dsr_count (db/get-dsr-count-for-userprofile {:userid userid}))
        dsa_count (:dsa_count (db/get-dsa-count-for-userprofile {:userid userid}))
        external_training_count (:external_training_count (db/get-my-trainings-count {:userid userid}))
        training_count (if-let [moodle-user-id (moodle/get-moodle-user userid)] (count (moodle/get-moodle-enrolled-courses moodle-user-id)) 0)
        projects_count (:projects_count (db/get-projects-count-for-userprofile {:userid userid}))]

    (if (seq userattrs-json)
      (cheshire-json/generate-string (append-remaining-key-value-pairs-to-json (json/parse-string userattrs-json) dsr_count dsa_count projects_count external_training_count training_count))
      (cheshire-json/generate-string {}))))

;; Append new key-value pair to the user dashboard mapping
(defn form-dashboard-reponse-json [existing-map name datasets_count projects_count dsrs_count dsas_count projects dsrs dsas]
  (assoc existing-map
         :name name
         :datasets_count datasets_count
         :projects_count projects_count
         :dsr_count dsrs_count
         :dsa_count dsas_count
         :projects projects
         :dsrs dsrs
         :dsas dsas))


(defn get-user-dashboard-data
  "fetch dashboard data"
  [userid]
  (log/info "userid == " userid)
  (let [userattrs-json (:userattrs (db/get-user-profile {:userid userid}))
        dsr_count (:dsr_count (db/get-dsr-count-for-userprofile {:userid userid}))
        dsa_count (:dsa_count (db/get-dsa-count-for-userprofile {:userid userid}))
        external_training_count (:external_training_count (db/get-my-trainings-count {:userid userid}))
        training_count (if-let [moodle-user-id (moodle/get-moodle-user userid)] (count (moodle/get-moodle-enrolled-courses moodle-user-id)) 0)
        projects_count (:projects_count (db/get-projects-count-for-userprofile {:userid userid}))
        datasets_count 0
        projects (db/get-dashboard-projects-tabular-data {:userid userid})
        dsas (db/get-dashboard-dsas-tabular-data {:userid userid})
        dsrs (db/get-dashboard-dsrs-tabular-data {:userid userid})]

    (if (seq userattrs-json)
      (do
        (log/info "success..")
        (cheshire-json/generate-string (form-dashboard-reponse-json {} (:name (json/parse-string userattrs-json))
                                                                    datasets_count
                                                                    projects_count
                                                                    dsr_count
                                                                    dsa_count
                                                                    external_training_count
                                                                    training_count
                                                                    projects
                                                                    dsrs
                                                                    dsas)))
      (do
        (log/info "Fail..")
        (cheshire-json/generate-string {})))))

(defn fetch-user-details-based-on-user-email-id
  "Fetch the user details "
  [user-email-id]
  ;;(assert user-email-id "user-email-id cannot be NULL.")
  (log/info "user-email-id == " user-email-id)
  (let [userid (:userid (db/get-userid-from-user-email {:user-email-id user-email-id}))
        userattrs-json (:userattrs (db/get-user-profile {:userid userid}))
        dsr_count (:dsr_count (db/get-dsr-count-for-userprofile {:userid userid}))
        dsa_count (:dsa_count (db/get-dsa-count-for-userprofile {:userid userid}))
        external_training_count (:external_training_count (db/get-my-trainings-count {:userid userid}))
        training_count (if-let [moodle-user-id (moodle/get-moodle-user userid)] (count (moodle/get-moodle-enrolled-courses moodle-user-id)) 0)
        projects_count (:projects_count (db/get-projects-count-for-userprofile {:userid userid}))]

    (if (seq userattrs-json)
      (cheshire-json/generate-string (append-remaining-key-value-pairs-to-json (json/parse-string userattrs-json) dsr_count dsa_count projects_count external_training_count training_count))
      (cheshire-json/generate-string {}))))

(defn fetch-logged-in-user-role
  "Fetch the user roles of the current logged-in user"
  [user-id]
  (let [user-roles (:role (db/get-logged-in-user-roles {:user-id user-id}))]
    (if (empty? user-roles)
      (cheshire-json/generate-string {:role "logged-in"})
      (cheshire-json/generate-string {:role user-roles}))))

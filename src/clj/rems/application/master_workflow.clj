(ns rems.application.master-workflow
  "The master workflow is a superset of all possible commands and who is
  allowed to execute them. Workflows for production use can be derived from
  the master workflow by restricting the possible commands (permissions)."
  (:require [rems.application.commands :as commands]
            [rems.permissions :as permissions]))

(def whitelist
  (permissions/compile-rules
   (into [{:permission :see-everything}]
         (map (fn [command]
                {:permission command})
              (sort (keys commands/command-schemas))))))

(defmulti application-permissions-view
  (fn [_application event] (:event/type event)))

(defmethod application-permissions-view :default
  [application _event]
  application)

(def ^:private invitation-commands
  #{:application.command/accept-invitation
    :application.command/decline-invitation})

(defn add-invitation-permissions [permissions]
  (into permissions (seq invitation-commands)))

(def ^:private submittable-application-commands
  #{:application.command/save-draft
    :application.command/submit
    :application.command/remove-member
    :application.command/invite-member
    :application.command/uninvite-member
    :application.command/accept-licenses
    :application.command/change-resources
    :application.command/copy-as-new})

(def ^:private non-submittable-application-commands
  #{:application.command/remove-member
    :application.command/uninvite-member
    :application.command/accept-licenses
    :application.command/copy-as-new})

(def ^:private handler-all-commands
  #{:application.command/remark
    :application.command/add-licenses
    :application.command/add-member
    :application.command/assign-external-id
    :application.command/change-resources
    :application.command/redact-attachments
    :application.command/remove-member
    :application.command/invite-decider
    :application.command/invite-member
    :application.command/invite-reviewer
    :application.command/uninvite-member
    :application.command/change-applicant
    :application.command/request-review
    :application.command/request-decision
    :application.command/return
    :application.command/approve
    :application.command/handler-update-form
    :application.command/reject
    :application.command/close})

(def ^:private handler-returned-commands
  (disj handler-all-commands
        :application.command/return
        :application.command/approve
        :application.command/reject
        :application.command/handler-update-form
        :application.command/request-decision))

(def ^:private created-permissions
  {:applicant (conj submittable-application-commands
                    :application.command/delete)
   :member #{:application.command/accept-licenses
             :application.command/copy-as-new}
   :reporter (add-invitation-permissions #{:see-everything})
   :expirer #{:application.command/delete
              :application.command/send-expiration-notifications}
   ;; member before accepting an invitation
   :handler invitation-commands
   :reviewer invitation-commands
   :past-reviewer invitation-commands
   :decider invitation-commands
   :past-decider invitation-commands
   :everyone-else invitation-commands})

(def ^:private submitted-permissions
  {:applicant non-submittable-application-commands
   :handler (conj handler-all-commands :see-everything)
   :reviewer #{:see-everything
               :application.command/redact-attachments
               :application.command/remark
               :application.command/review}
   :past-reviewer #{:see-everything
                    :application.command/redact-attachments
                    :application.command/remark}
   :decider #{:see-everything
              :application.command/redact-attachments
              :application.command/remark
              :application.command/decide
              :application.command/approve
              :application.command/reject}
   :past-decider #{:see-everything
                   :application.command/redact-attachments
                   :application.command/remark}
   :everyone-else #{:application.command/decline-invitation}})

(def ^:private returned-permissions
  {:applicant (conj submittable-application-commands
                    :application.command/close)
   :handler (add-invitation-permissions (conj handler-returned-commands :see-everything))
   :decider (add-invitation-permissions #{:see-everything
                                          :application.command/redact-attachments
                                          :application.command/remark
                                          :application.command/decide})
   :reviewer invitation-commands
   :past-reviewer invitation-commands
   :past-decider invitation-commands
   :everyone-else invitation-commands})

(def ^:private approved-permissions
  {:applicant non-submittable-application-commands
   :handler #{:see-everything
              :application.command/redact-attachments
              :application.command/remark
              :application.command/add-member
              :application.command/change-resources
              :application.command/remove-member
              :application.command/invite-member
              :application.command/uninvite-member
              :application.command/close
              :application.command/revoke
              :application.command/decline-invitation}
   :decider #{:see-everything
              :application.command/redact-attachments
              :application.command/remark
              :application.command/decide
              :application.command/decline-invitation}
   :reviewer #{:application.command/decline-invitation}
   :past-reviewer #{:application.command/decline-invitation}
   :past-decider #{:application.command/decline-invitation}
   :everyone-else #{:application.command/decline-invitation}})

(def ^:private closed-permissions
  {:applicant #{:application.command/copy-as-new}
   :member #{:application.command/copy-as-new
             :application.command/decline-invitation}
   :handler #{:see-everything
              :application.command/redact-attachments
              :application.command/remark
              :application.command/decline-invitation}
   :reviewer #{:see-everything
               :application.command/decline-invitation}
   :past-reviewer #{:see-everything
                    :application.command/decline-invitation}
   :decider #{:see-everything
              :application.command/decline-invitation}
   :past-decider #{:see-everything
                   :application.command/decline-invitation}
   :everyone-else #{:application.command/decline-invitation}})

(defmethod application-permissions-view :application.event/created
  [application event]
  (-> application
      (permissions/give-role-to-users :applicant [(:event/actor event)])
      (permissions/update-role-permissions created-permissions)))

(defmethod application-permissions-view :application.event/member-added
  [application event]
  (-> application
      (permissions/give-role-to-users :member [(get-in event [:application/member :userid])])))

(defmethod application-permissions-view :application.event/member-joined
  [application event]
  (-> application
      (permissions/give-role-to-users :member [(:event/actor event)])))

(defmethod application-permissions-view :application.event/member-removed
  [application event]
  (-> application
      (permissions/remove-role-from-user :member (get-in event [:application/member :userid]))))

(defmethod application-permissions-view :application.event/applicant-changed
  [application event]
  (let [old (first (for [[user roles] (:application/user-roles application)
                         :when (contains? roles :applicant)]
                     user))
        new (get-in event [:application/applicant :userid])]
    (-> application
        (permissions/remove-role-from-user :applicant old)
        (permissions/give-role-to-users :member [old])
        (permissions/give-role-to-users :applicant [new])
        (permissions/remove-role-from-user :member new))))

(defmethod application-permissions-view :application.event/submitted
  [application _event]
  (-> application
      (permissions/update-role-permissions submitted-permissions)))

(defmethod application-permissions-view :application.event/returned
  [application _event]
  (-> application
      (permissions/update-role-permissions returned-permissions)))

(defmethod application-permissions-view :application.event/review-requested
  [application event]
  (-> application
      (permissions/give-role-to-users :reviewer (:application/reviewers event))))

(defmethod application-permissions-view :application.event/reviewer-joined
  [application event]
  (-> application
      (permissions/give-role-to-users :reviewer [(:event/actor event)])))

(defmethod application-permissions-view :application.event/reviewed
  [application event]
  (-> application
      (permissions/remove-role-from-user :reviewer (:event/actor event))
      (permissions/give-role-to-users :past-reviewer [(:event/actor event)]))) ; allow to still view the application

(defmethod application-permissions-view :application.event/decision-requested
  [application event]
  (-> application
      (permissions/give-role-to-users :decider (:application/deciders event))))

(defmethod application-permissions-view :application.event/decider-joined
  [application event]
  (-> application
      (permissions/give-role-to-users :decider [(:event/actor event)])))

(defmethod application-permissions-view :application.event/decided
  [application event]
  (-> application
      (permissions/remove-role-from-user :decider (:event/actor event))
      (permissions/give-role-to-users :past-decider [(:event/actor event)]))) ; allow to still view the application

(defmethod application-permissions-view :application.event/approved
  [application _event]
  (-> application
      (permissions/update-role-permissions approved-permissions)))

(defmethod application-permissions-view :application.event/rejected
  [application _event]
  (-> application
      (permissions/update-role-permissions closed-permissions)))

(defmethod application-permissions-view :application.event/closed
  [application _event]
  (-> application
      (permissions/update-role-permissions closed-permissions)))

(defmethod application-permissions-view :application.event/revoked
  [application _event]
  (-> application
      (permissions/update-role-permissions closed-permissions)))

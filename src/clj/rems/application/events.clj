(ns rems.application.events
  (:require [clojure.test :refer :all]
            [schema-refined.core :as r]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema EventBase
  {(s/optional-key :event/id) s/Int
   :event/type s/Keyword
   :event/time DateTime
   :event/actor UserId
   :application/id s/Int})

(s/defschema ApprovedEvent
  (assoc EventBase
         ;; single-value enums are supported by swagger, unlike s/eq.
         ;; we don't yet generate swagger for events but we might in
         ;; the future
         :event/type (s/enum :application.event/approved)
         :application/comment s/Str))
(s/defschema ClosedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/closed)
         :application/comment s/Str))
(s/defschema CommentedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/commented)
         :application/request-id s/Uuid
         :application/comment s/Str))
(s/defschema CommentRequestedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/comment-requested)
         :application/request-id s/Uuid
         :application/commenters [s/Str]
         :application/comment s/Str))
(s/defschema CreatedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/created)
         :application/resources [{:catalogue-item/id s/Int
                                  :resource/ext-id s/Str}]
         :application/licenses [{:license/id s/Int}]
         :form/id s/Int
         :workflow/id s/Int
         :workflow/type s/Keyword
         :application/external-id (s/maybe s/Str)
         ;; workflow-specific data
         (s/optional-key :workflow.dynamic/handlers) #{s/Str}))
(s/defschema DecidedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/decided)
         :application/request-id s/Uuid
         :application/decision (s/enum :approved :rejected)
         :application/comment s/Str))
(s/defschema DecisionRequestedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/decision-requested)
         :application/request-id s/Uuid
         :application/deciders [s/Str]
         :application/comment s/Str))
(s/defschema DraftSavedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/draft-saved)
         :application/field-values {s/Int s/Str}))
(s/defschema LicensesAcceptedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/licenses-accepted)
         :application/accepted-licenses #{s/Int}))
(s/defschema LicensesAddedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/licenses-added)
         :application/comment s/Str
         :application/licenses #{s/Int}))
(s/defschema MemberAddedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-added)
         :application/member {:userid UserId}))
(s/defschema MemberInvitedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-invited)
         :application/member {:name s/Str
                              :email s/Str}
         :invitation/token s/Str))
(s/defschema MemberJoinedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-joined)
         :invitation/token s/Str))
(s/defschema MemberRemovedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-removed)
         :application/member {:userid UserId}
         :application/comment s/Str))
(s/defschema MemberUninvitedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/member-uninvited)
         :application/member {:name s/Str
                              :email s/Str}
         :application/comment s/Str))
(s/defschema RejectedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/rejected)
         :application/comment s/Str))
(s/defschema ReturnedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/returned)
         :application/comment s/Str))
(s/defschema SubmittedEvent
  (assoc EventBase
         :event/type (s/enum :application.event/submitted)))

(def event-schemas
  {:application.event/approved ApprovedEvent
   :application.event/closed ClosedEvent
   :application.event/commented CommentedEvent
   :application.event/comment-requested CommentRequestedEvent
   :application.event/created CreatedEvent
   :application.event/decided DecidedEvent
   :application.event/decision-requested DecisionRequestedEvent
   :application.event/draft-saved DraftSavedEvent
   :application.event/licenses-accepted LicensesAcceptedEvent
   :application.event/licenses-added LicensesAddedEvent
   :application.event/member-added MemberAddedEvent
   :application.event/member-invited MemberInvitedEvent
   :application.event/member-joined MemberJoinedEvent
   :application.event/member-removed MemberRemovedEvent
   :application.event/member-uninvited MemberUninvitedEvent
   :application.event/rejected RejectedEvent
   :application.event/returned ReturnedEvent
   :application.event/submitted SubmittedEvent})

(s/defschema Event
  (apply r/dispatch-on :event/type (flatten (seq event-schemas))))

(defn validate-event [event]
  (s/validate Event event))

(defn validate-events [events]
  (doseq [event events]
    (validate-event event))
  events)

(deftest test-event-schema
  (testing "check specific event schema"
    (is (nil? (s/check SubmittedEvent {:event/type :application.event/submitted
                                       :event/time (DateTime.)
                                       :event/actor "foo"
                                       :application/id 123}))))
  (testing "check generic event schema"
    (is (nil? (s/check Event
                       {:event/type :application.event/submitted
                        :event/time (DateTime.)
                        :event/actor "foo"
                        :application/id 123})))
    (is (nil? (s/check Event
                       {:event/type :application.event/approved
                        :event/time (DateTime.)
                        :event/actor "foo"
                        :application/id 123
                        :application/comment "foo"}))))
  (testing "missing event specific key"
    (is (= {:application/comment 'missing-required-key}
           (s/check Event
                    {:event/type :application.event/approved
                     :event/time (DateTime.)
                     :event/actor "foo"
                     :application/id 123}))))
  (testing "unknown event type"
    ;; TODO: improve error message to show the actual and expected event types
    (is (= "(not (some-matching-condition? a-clojure.lang.PersistentArrayMap))"
           (pr-str (s/check Event
                            {:event/type :foo
                             :event/time (DateTime.)
                             :event/actor "foo"
                             :application/id 123}))))))

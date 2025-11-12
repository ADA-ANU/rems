(ns rems.schema-base
  "Fragments of schema shared between API, event and command schemas.

  Be careful when adding things here: we don't want to couple the API
  schema too tightly to internal schemas!"
  (:require [ring.swagger.json-schema :as rjs]
            [schema.core :as s]
            [clojure.string :as str])
  (:import (org.joda.time DateTime)))

(def NonEmptyString
  (s/pred (fn [s]
            (and (string? s)
                 (not (str/blank? s))))
          'NonEmptyString))

;; can't use defschema for this alias since s/Str is just String, which doesn't have metadata
(def UserId s/Str)

(s/defschema User {:userid UserId})

(def FieldId s/Str)

(def FormId s/Int)

(def EventId s/Int) ; used both optionally and as required

(s/defschema OrganizationId {:organization/id NonEmptyString})

(s/defschema Language
  (rjs/field s/Keyword
             {:description "A language code"
              :example "en"}))

(s/defschema LocalizedString
  (rjs/field {Language s/Str}
             {:example {:fi "text in Finnish"
                        :en "text in English"}
              :description "Text values keyed by languages"}))

(s/defschema LocalizedNonEmptyString
  (rjs/field {Language NonEmptyString}
             {:example {:fi "text in Finnish"
                        :en "text in English"}
              :description "Text values keyed by languages"}))

(s/defschema LocalizedInt
  (rjs/field {Language s/Int}
             {:example {:fi 1
                        :en 2}
              :description "Integers keyed by languages"}))

;; cond-pre generates a x-oneOf schema, which is
;; correct, but swagger-ui doesn't render it. We would need
;; to switch from Swagger 2.0 specs to OpenAPI 3 specs to get
;; swagger-ui support. However ring-swagger only supports
;; Swagger 2.0.
;;
;; As a workaround, add a manual description
(s/defschema FieldValue
  (rjs/field
   (s/cond-pre s/Str [[{:column s/Str :value s/Str}]])
   {:example "value"
    :description "A string for most fields, or [[{\"column\": string, \"value\": string}]] for table fields"}))

(s/defschema EventBase
  {(s/optional-key :event/id) EventId
   :event/type s/Keyword
   :event/time DateTime
   :event/actor UserId
   :application/id s/Int})

(s/defschema UserWithAttributes
  {:userid UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :organizations) [OrganizationId]
   (s/optional-key :notification-email) (s/maybe s/Str)
   (s/optional-key :researcher-status-by) s/Str
   s/Keyword s/Any})

(s/defschema OrganizationOverview
  (merge OrganizationId
         {:organization/short-name LocalizedNonEmptyString
          :organization/name LocalizedNonEmptyString}))

(s/defschema OrganizationFull
  (merge OrganizationOverview
         {(s/optional-key :organization/modifier) UserWithAttributes
          (s/optional-key :organization/last-modified) DateTime
          (s/optional-key :organization/owners) [UserWithAttributes]
          (s/optional-key :organization/review-emails) [{:name LocalizedString
                                                         :email s/Str}]
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

(s/defschema MondoCode
  {:id s/Str})

(s/defschema MondoCodeFull
  (merge MondoCode
         {:label s/Str}))

(s/defschema DuoCode
  {:id s/Str
   (s/optional-key :restrictions) [{:type s/Keyword
                                    (s/optional-key :values) [(s/conditional :value {:value s/Str}
                                                                             :label MondoCodeFull
                                                                             :id MondoCode)]}]
   (s/optional-key :more-info) LocalizedString})

(s/defschema DuoCodeFull
  (merge DuoCode
         {(s/optional-key :shorthand) (s/maybe s/Str)
          :label LocalizedString
          :description LocalizedString}))

(s/defschema CategoryId
  {:category/id s/Int})

(s/defschema Category
  (merge CategoryId
         {:category/title LocalizedString
          (s/optional-key :category/description) LocalizedString
          (s/optional-key :category/display-order) s/Int
          (s/optional-key :category/children) [CategoryId]}))

(s/defschema CategoryFull
  (merge Category
         {(s/optional-key :category/children) [Category]}))

(s/defschema LicenseId
  {:license/id s/Int})

(s/defschema InvitationProject
  {(s/optional-key :project/id) s/Int
   (s/optional-key :project/description) s/Str
   (s/optional-key :project/name) LocalizedString})


(s/defschema InvitationResponse
  {(s/optional-key :invitation/id) s/Int
   (s/optional-key :invitation/token) s/Str
   :invitation/name s/Str
   :invitation/email s/Str
   :invitation/invited-by UserWithAttributes
   (s/optional-key :invitation/invited-user) UserWithAttributes
   :invitation/created DateTime
   (s/optional-key :invitation/sent) DateTime
   (s/optional-key :invitation/accepted) DateTime
   (s/optional-key :invitation/declined) DateTime
   (s/optional-key :invitation/left) DateTime
   (s/optional-key :invitation/revoked) DateTime
   (s/optional-key :invitation/revoked-by) UserWithAttributes
   (s/optional-key :invitation/workflow) {:workflow/id s/Int}
   (s/optional-key :invitation/project) InvitationProject
   (s/optional-key :invitation/role) s/Str})

(s/defschema CreateInvitationCommand
  {:name s/Str
   :email s/Str
   (s/optional-key :workflow-id) s/Int
   (s/optional-key :project-id) s/Int
   (s/optional-key :role) s/Str})

(s/defschema ApplicationIds
  {:id s/Int})
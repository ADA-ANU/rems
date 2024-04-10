(ns rems.schema-base-cadre
  "Fragments of schema shared between API, event and command schemas.

  Be careful when adding things here: we don't want to couple the API
  schema too tightly to internal schemas!"
  (:require [rems.schema-base :as schema-base]
            [schema.core :as s]
            [rems.api.schema :as schema])
  (:import (org.joda.time DateTime)))

(def ProjectId s/Int) ; used both optionally and as required

(s/defschema ProjectOrganisationRole
  {:id s/Str
  :schemaUri s/Str
  :startDate DateTime
  (s/optional-key :endDate) DateTime
})

(s/defschema ProjectOrganisation
  {:id s/Str
  :schemaUri s/Str
  :name s/Str
  (s/optional-key :role) ProjectOrganisationRole
})

(s/defschema ProjectApplications
  {:id s/Int})

(s/defschema UserWithAttributesCadre
  {:userid schema-base/UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :projects) [ProjectId]
   (s/optional-key :organizations) [schema-base/OrganizationId]
   (s/optional-key :notification-email) (s/maybe s/Str)
   (s/optional-key :researcher-status-by) s/Str
   (s/optional-key :eduPersonOrcid) s/Str
   s/Keyword s/Any})

(s/defschema ProjectOverview
  {(s/optional-key :project/id) ProjectId
   :project/name schema-base/LocalizedString})

(s/defschema ProjectApplication
  (merge ProjectOverview
         {(s/optional-key :project/last-modified) DateTime
          (s/optional-key :project/owners) [UserWithAttributesCadre]
          (s/optional-key :project/collaborators) [UserWithAttributesCadre]
          (s/optional-key :project/applications) [ProjectApplications]
          (s/optional-key :project/RAiD) s/Str
          (s/optional-key :project/organisations) [ProjectOrganisation]
          (s/optional-key :project/start-date) DateTime
          (s/optional-key :project/description) s/Str
          (s/optional-key :project/end-date) DateTime
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

(s/defschema ProjectFull
  (merge ProjectOverview
         {(s/optional-key :project/last-modified) DateTime
          (s/optional-key :project/owners) [UserWithAttributesCadre]
          (s/optional-key :project/collaborators) [UserWithAttributesCadre]
          (s/optional-key :project/applications) [schema/ApplicationRaw]
          (s/optional-key :project/RAiD) s/Str
          (s/optional-key :project/organisations) [ProjectOrganisation]
          (s/optional-key :project/start-date) DateTime
          (s/optional-key :project/description) s/Str
          (s/optional-key :project/end-date) DateTime
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

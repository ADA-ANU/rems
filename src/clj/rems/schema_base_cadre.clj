(ns rems.schema-base-cadre
  "Fragments of schema shared between API, event and command schemas.

  Be careful when adding things here: we don't want to couple the API
  schema too tightly to internal schemas!"
  (:require [rems.schema-base :as schema-base]
            [schema.core :as s])
  (:import (org.joda.time DateTime)))

(s/defschema ProjectId {:project/id s/Int})

(s/defschema UserWithAttributesCadre
  {:userid schema-base/UserId
   :name (s/maybe s/Str)
   :email (s/maybe s/Str)
   (s/optional-key :projects) [ProjectId]
   (s/optional-key :organizations) [schema-base/OrganizationId]
   (s/optional-key :notification-email) (s/maybe s/Str)
   (s/optional-key :researcher-status-by) s/Str
   s/Keyword s/Any})

(s/defschema ProjectOverview
  (merge ProjectId
         {:project/short-name schema-base/LocalizedString
          :project/name schema-base/LocalizedString}))

(s/defschema ProjectFull
  (merge ProjectOverview
         {(s/optional-key :project/modifier) UserWithAttributesCadre
          (s/optional-key :project/last-modified) DateTime
          (s/optional-key :project/owners) [UserWithAttributesCadre]
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

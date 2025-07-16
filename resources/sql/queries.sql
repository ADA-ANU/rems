-- The queries here get transformed into functions in rems.db.core.
-- See rems.db.core and docs/architecture/012-layers.md for more info.

-- :name get-catalogue-items :? :*
-- :doc
-- - Get catalogue items
-- - :ids vector of item ids
-- - :resource resource external id to fetch items for
-- - :resource-id resource internal id to fetch items for
-- - :workflow workflow id to fetch items for
-- - :form form id to fetch items for
-- - :archived true if archived items should be included
-- - :enabled whether enabled items should be included or nil if doesn't matter
-- - :limit Number of items in each page
-- - :offset Number of pages
SELECT ci.id, res.resid, ci.wfid, ci.formid, ci.start, ci.endt as "end", ci.enabled, ci.archived, ci.organization
/*~ (when (:expand-catalogue-data? params) */
, ci.catalogueitemdata::TEXT
/*~ ) ~*/
, res.id AS "resource-id"
/*~ (when (:expand-resource-data? params) */
, res.resourcedata::TEXT AS "resourcedata"
/*~ ) ~*/
/*~ (when (:expand-names? params) */
, wf.title AS "workflow-name"
, res.resid AS "resource-name"
, form.formdata->>'form/internal-name' AS "form-name"
/*~ ) ~*/
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
/*~ (when (:expand-names? params) */
LEFT OUTER JOIN workflow wf ON (ci.wfid = wf.id)
LEFT OUTER JOIN form_template form ON (ci.formid = form.id)
/*~ ) ~*/
WHERE 1=1
/*~ (when (:ids params) */
  AND ci.id IN (:v*:ids)
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.resid = :resource
/*~ ) ~*/
/*~ (when (:resource-id params) */
  AND ci.resid = :resource-id
/*~ ) ~*/
/*~ (when (:form params) */
  AND ci.formid = :form
/*~ ) ~*/
/*~ (when (:workflow params) */
  AND ci.wfid = :workflow
/*~ ) ~*/
/*~ (when (not (:archived params)) */
  AND ci.archived = false
/*~ ) ~*/
/*~ (when-not (nil? (:enabled params)) */
  AND ci.enabled = :enabled
/*~ ) ~*/
/*~ (when (:userid params) */
  AND ci.organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :userid

    UNION ALL

    SELECT
      DISTINCT org.id
    FROM
      workflow w,
      organization org,
      LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
    WHERE
      handlers::text = :userid
      AND w.organization = org.id
      AND w.enabled = true
      AND w.archived = false
  )
/*~ ) ~*/
  ORDER BY ci.id
/*~ (when (:limit params) */
  LIMIT :limit
/*~ ) ~*/
/*~ (when (:offset params) */ 
  OFFSET :offset
/*~ ) ~*/
  ;

-- :name set-catalogue-item-enabled! :!
UPDATE catalogue_item
SET enabled = :enabled
WHERE id = :id;

-- :name set-catalogue-item-archived! :!
UPDATE catalogue_item
SET archived = :archived
WHERE id = :id;

-- :name set-catalogue-item-endt! :!
-- TODO only used for creating test data. either have proper API
--      for using this or remove?
UPDATE catalogue_item
SET endt = :end
WHERE id = :id;

-- :name set-catalogue-item-organization! :!
UPDATE catalogue_item
SET organization = :organization
WHERE id = :id;

-- :name set-catalogue-item-data! :!
UPDATE catalogue_item
SET catalogueitemdata = :catalogueitemdata::jsonb
WHERE id = :id;

-- :name create-catalogue-item! :insert
-- :doc Create a single catalogue item
INSERT INTO catalogue_item
(formid, resid, wfid, organization, enabled, archived, start, catalogueitemdata)
VALUES (:form, :resid, :wfid, :organization,
--~ (if (contains? params :enabled) ":enabled" "true")
,
--~ (if (contains? params :archived) ":archived" "false")
,
--~ (if (contains? params :start) ":start" "now()")
,
/*~ (if (contains? params :catalogueitemdata) */ :catalogueitemdata::jsonb /*~*/ NULL /*~ ) ~*/
);

-- :name get-resources :? :*
SELECT
  id,
  organization,
  resid,
  enabled,
  archived,
  resourcedata::TEXT
FROM resource
WHERE 
  1=1
/*~ (when (:userid params) */
  AND organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :userid

    UNION ALL

    SELECT
      DISTINCT org.id
    FROM
      workflow w,
      organization org,
      LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
    WHERE
      handlers::text = :userid
      AND w.organization = org.id
      AND w.enabled = true
      AND w.archived = false
  )
/*~ ) ~*/
/*~ (when (:resid params) */
  AND resid = :resid
/*~ ) ~*/
;

-- :name get-resource :? :1
SELECT
  id,
  organization,
  resid,
  enabled,
  archived,
  resourcedata::TEXT
FROM resource
WHERE 1=1
/*~ (when (:id params) */
  AND id = :id
/*~ ) ~*/
/*~ (when (:resid params) */
  AND resid = :resid
/*~ ) ~*/
/*~ (when (:userid params) */
  AND organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :userid

    UNION ALL

    SELECT
      DISTINCT org.id
    FROM
      workflow w,
      organization org,
      LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
    WHERE
      handlers::text = :userid
      AND w.organization = org.id
      AND w.enabled = true
      AND w.archived = false
  )
/*~ ) ~*/
;

-- :name create-resource! :insert
-- :doc Create a single resource
INSERT INTO resource
(resid, organization, resourcedata)
VALUES (:resid, :organization, :resourcedata::jsonb);

-- :name update-resource! :!
UPDATE resource
SET (resid, organization, resourcedata) = (:resid, :organization, :resourcedata::jsonb)
WHERE id = :id;

-- :name set-resource-enabled! :!
UPDATE resource
SET enabled = :enabled
WHERE id = :id;

-- :name set-resource-archived! :!
UPDATE resource
SET archived = :archived
WHERE id = :id;

-- :name get-database-name :? :1
SELECT current_database();

-- :name get-catalogue-item-localizations :? :*
SELECT catid AS id, langcode, title, infoUrl
FROM catalogue_item_localization;

-- :name upsert-catalogue-item-localization! :insert
-- TODO now that we have the catalogue_item_localization_unique
-- constraint, we can get rid of the synthetic id column
INSERT INTO catalogue_item_localization
  (catid, langcode, title, infoUrl)
VALUES (:id, :langcode, :title,
  /*~ (if (:infourl params) */ :infourl /*~*/ NULL /*~ ) ~*/
  )
ON CONFLICT (catid, langcode)
DO UPDATE
SET (catid, langcode, title) = (:id, :langcode, :title)
--~ (when (contains? params :infourl) ", infoUrl = :infourl")

-- :name get-form-templates :? :*
SELECT
  id,
  organization,
  formdata->>'form/internal-name' AS title,
  formdata::TEXT,
  fields::TEXT,
  enabled,
  archived
FROM form_template
WHERE 
  1=1
/*~ (when (:own params) */
  AND organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :own
  )
/*~ ) ~*/
/*~ (when (:associated params) */
  AND (
    organization IN (
      SELECT
        DISTINCT org.id
      FROM
        organization org,
        LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
      WHERE
        owners->>'userid' = :associated
    )
    OR id IN (
      SELECT
        DISTINCT ((jsonb_array_elements(w.workflowbody->'forms'))->'form/id')::int As forms
      FROM
        workflow w,
        LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        w.enabled = true
        AND w.archived = false
        AND handlers::text = :associated

      UNION

      SELECT 
        DISTINCT item.formid
      FROM catalogue_item item
      INNER JOIN workflow w ON item.wfid = w.id
      CROSS JOIN LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        w.enabled = true
        AND w.archived = false
        AND handlers::text = :associated
    )
  )
/*~ ) ~*/
;

-- :name get-form-template :? :1
SELECT
  id,
  organization,
  formdata::TEXT,
  fields::TEXT,
  enabled,
  archived
FROM form_template
WHERE 
  id = :id
/*~ (when (:userid params) */
  AND (
    organization IN (
      SELECT
        DISTINCT org.id
      FROM
        organization org,
        LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
      WHERE
        owners->>'userid' = :userid
    )
    OR id IN (
      SELECT
        DISTINCT ((jsonb_array_elements(w.workflowbody->'forms'))->'form/id')::int As forms
      FROM
        workflow w,
        LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        w.enabled = true
        AND w.archived = false
        AND handlers::text = :userid

      UNION

      SELECT 
        DISTINCT item.formid
      FROM catalogue_item item
      INNER JOIN workflow w ON item.wfid = w.id
      CROSS JOIN LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        w.enabled = true
        AND w.archived = false
        AND handlers::text = :userid
    )
  )
/*~ ) ~*/
;

-- :name save-form-template! :insert
INSERT INTO form_template
(organization, fields, formdata)
VALUES
(:organization,
 :fields::jsonb,
 :formdata::jsonb
);

-- :name edit-form-template! :!
UPDATE form_template
SET (organization, fields, formdata) =
(:organization,
 :fields::jsonb,
 :formdata::jsonb)
WHERE
id = :id;

-- :name update-form-template! :!
UPDATE form_template
SET (organization, fields, formdata) =
(:organization,
 :fields::jsonb,
 :formdata::jsonb)
WHERE
id = :id;

-- :name set-form-template-enabled! :!
UPDATE form_template
SET enabled = :enabled
WHERE
id = :id;

-- :name set-form-template-archived! :!
UPDATE form_template
SET archived = :archived
WHERE
id = :id;

-- :name create-application! :insert
INSERT INTO catalogue_item_application (id)
VALUES (nextval('catalogue_item_application_id_seq'))
RETURNING id;

-- :name get-application-ids :?
SELECT id FROM catalogue_item_application;

-- :name delete-application! :!
DELETE FROM catalogue_item_application
WHERE id = :application;

-- :name add-entitlement! :!
INSERT INTO entitlement (catAppId, userId, resId, approvedby, start, endt)
VALUES (:application, :user, :resource, :approvedby, :start,
/*~ (if (:end params) */ :end /*~*/ NULL /*~ ) ~*/
);

-- :name update-entitlement! :!
UPDATE entitlement
SET (catAppId, userId, resId, approvedby, start, endt, revokedby)
= (:application, :user, :resource, :approvedby, :start,
/*~ (if (:end params) */ :end /*~*/ NULL /*~ ) ~*/,
/*~ (if (:revokedby params) */ :revokedby /*~*/ NULL /*~ ) ~*/
)
WHERE id = :id;

-- :name end-entitlements! :!
UPDATE entitlement
SET (endt, revokedby) = (:end, :revokedby)
WHERE catAppId = :application
/*~ (when (:user params) */
  AND entitlement.userId = :user
/*~ ) ~*/
/*~ (when (:resource params) */
  AND entitlement.resId = :resource
/*~ ) ~*/
;

-- :name get-entitlements-still-active :?
-- :doc
-- Params:
--   :application -- application id to ignore checking
--   :user -- user id to limit select to
--   :resource -- resid to limit select to
--   :active-at -- only return entitlements with start<=active-at<end (or end undefined)
SELECT entitlement.id AS entitlementId, res.id AS resourceId, res.resId, catAppId, entitlement.userId, entitlement.start, entitlement.endt AS "end", users.userAttrs->>'email' AS mail,
entitlement.approvedby FROM entitlement
LEFT OUTER JOIN resource res ON entitlement.resId = res.id
LEFT OUTER JOIN users on entitlement.userId = users.userId
WHERE 1=1
/*~ (when (:application params) */
  AND catAppId != :application
/*~ ) ~*/
/*~ (when (:user params) */
  AND entitlement.userId = :user
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.id = :resource
/*~ ) ~*/
/*~ (when (:resource-ext-id params) */
  AND res.resid = :resource-ext-id
/*~ ) ~*/
/*~ (when (:active-at params) */
  AND entitlement.start <= :active-at AND (entitlement.endt is NULL OR :active-at < entitlement.endt)
/*~ ) ~*/
ORDER BY entitlement.userId, res.resId, catAppId, entitlement.start, entitlement.endt;


-- :name get-entitlements :?
-- :doc
-- Params:
--   :application -- application id to limit select to
--   :user -- user id to limit select to
--   :resource -- resid to limit select to
--   :active-at -- only return entitlements with start<=active-at<end (or end undefined)
SELECT entitlement.id AS entitlementId, res.id AS resourceId, res.resId, catAppId, entitlement.userId, entitlement.start, entitlement.endt AS "end", users.userAttrs->>'email' AS mail,
entitlement.approvedby FROM entitlement
LEFT OUTER JOIN resource res ON entitlement.resId = res.id
LEFT OUTER JOIN users on entitlement.userId = users.userId
WHERE 1=1
/*~ (when (:application params) */
  AND catAppId = :application
/*~ ) ~*/
/*~ (when (:user params) */
  AND entitlement.userId = :user
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.id = :resource
/*~ ) ~*/
/*~ (when (:resource-ext-id params) */
  AND res.resid = :resource-ext-id
/*~ ) ~*/
/*~ (when (:active-at params) */
  AND entitlement.start <= :active-at AND (entitlement.endt is NULL OR :active-at < entitlement.endt)
/*~ ) ~*/
ORDER BY entitlement.userId, res.resId, catAppId, entitlement.start, entitlement.endt;

-- :name save-attachment! :insert
INSERT INTO attachment
(appId, userid, filename, type, data)
VALUES
(:application, :user, :filename, :type, :data);

-- :name update-attachment! :!
UPDATE attachment
SET (appId, userId, filename, type) = (:application, :user, :filename, :type)
WHERE id = :id;

-- :name redact-attachment! :!
UPDATE attachment
SET data = decode('', 'hex')
WHERE id = :id;

-- :name get-attachment :? :1
SELECT id, appid, filename, userId, type, data FROM attachment
WHERE id = :id;

-- :name get-attachments :? :*
SELECT id, appid, filename, userId, type
FROM attachment;

-- :name get-attachment-metadata :? :1
SELECT id, appid, filename, userId, type FROM attachment
WHERE id = :id;

-- :name get-attachments-for-application :? :*
SELECT id, filename, type, userId FROM attachment
WHERE appid = :application-id;

-- :name delete-application-attachments! :!
DELETE FROM attachment
WHERE appid = :application;

-- :name delete-application-comments! :!
DELETE FROM comments
WHERE appId = :application;

-- :name delete-attachment! :!
DELETE FROM attachment
WHERE id = :id;

-- :name create-license! :insert
INSERT INTO license
(organization, type)
VALUES
(:organization, :type::license_type)

-- :name set-license-enabled! :!
UPDATE license
SET enabled = :enabled
WHERE id = :id;

-- :name set-license-archived! :!
UPDATE license
SET archived = :archived
WHERE id = :id;

-- :name update-license! :!
UPDATE license
SET (organization, type, enabled, archived) = (:organization, :type::license_type, :enabled, :archived)
WHERE id = :id;

-- :name create-license-attachment! :insert
INSERT INTO license_attachment
(userId, filename, type, data, start)
VALUES
(:user, :filename, :type, :data, :start);

-- :name remove-license-attachment! :!
DELETE FROM license_attachment WHERE id = :id;

-- :name get-license-attachment :? :1
SELECT filename, type, data FROM license_attachment
WHERE id = :attachmentId;

-- :name create-license-localization! :insert
INSERT INTO license_localization
(licid, langcode, title, textcontent, attachmentId)
VALUES
(:licid, :langcode, :title, :textcontent,
/*~ (if (:attachmentId params) */ :attachmentId /*~*/ NULL /*~ ) ~*/
);

-- :name create-workflow! :insert
INSERT INTO workflow
(organization, title, workflowBody)
VALUES
(:organization,
 :title,
 /*~ (if (:workflow params) */ :workflow::jsonb /*~*/ NULL /*~ ) ~*/
);

-- :name set-workflow-enabled! :!
UPDATE workflow
SET enabled = :enabled
WHERE id = :id;

-- :name set-workflow-archived! :!
UPDATE workflow
SET archived = :archived
WHERE id = :id;

-- :name edit-workflow! :!
UPDATE workflow
SET
/*~ (when (:title params) */
  title = :title,
/*~ ) ~*/
/*~ (when (:workflow params) */
  workflowBody = :workflow::jsonb,
/*~ ) ~*/
/*~ (when (:organization params) */
  organization = :organization,
/*~ ) ~*/
  id = id
WHERE id = :id;

-- TODO: consider renaming this to link-resource-license!
-- :name create-resource-license! :insert
INSERT INTO resource_licenses
(resid, licid)
VALUES
(:resid, :licid);

-- :name get-workflow :? :1
SELECT
  wf.id, wf.organization, wf.title,
  wf.workflowBody::TEXT as workflow, wf.enabled, wf.archived
FROM workflow wf
/*~ (when (:catid params) */
JOIN catalogue_item ci ON (wf.id = ci.wfid)
/*~ ) ~*/
WHERE 
  1=1
/*~ (when (:wfid params) */
  AND wf.id = :wfid
/*~ ) ~*/
/*~ (when (:catid params) */
  AND ci.id = :catid
/*~ ) ~*/
/*~ (when (:userid params) */
  AND (
    wf.organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :userid
    )

    OR wf.id in (
      SELECT
        DISTINCT w.id
      FROM
        workflow w,
        LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        handlers::text = :userid
        AND w.enabled = true
        AND w.archived = false
    )
  )
/*~ ) ~*/
;

-- :name get-workflows :? :*
SELECT
  wf.id, wf.organization, wf.title,
  wf.workflowBody::TEXT as workflow, wf.enabled, wf.archived
FROM 
  workflow wf
WHERE 
  1=1
/*~ (when (:own params) */
  AND wf.organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :own
  )
/*~ ) ~*/
/*~ (when (:associated params) */
  AND (
    wf.organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :associated
    )

    OR wf.id in (
      SELECT
        DISTINCT w.id
      FROM
        workflow w,
        LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        handlers::text = :associated
        AND w.enabled = true
        AND w.archived = false
    )
  )
/*~ ) ~*/;

-- :name get-licenses :? :*
-- :doc
-- - Gets application licenses by catalogue item ids
-- - :items vector of catalogue item ids for resource licenses
SELECT lic.id, lic.type, lic.enabled, lic.archived, lic.organization
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
WHERE item.id IN (:v*:items)
ORDER BY id;

-- :name get-application-projects :? :*
SELECT proj.project_id as id, proj.projectattrs::text as data
FROM projects proj
INNER JOIN project_application pa ON proj.project_id = pa.projectid
WHERE pa.appid = :id;

-- :name get-resource-licenses :? :*
SELECT lic.id, lic.type, lic.enabled, lic.archived, lic.organization
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
WHERE rl.resid = :id;

-- :name get-all-licenses :? :*
SELECT lic.id, lic.type, lic.enabled, lic.archived, lic.organization
FROM license lic
WHERE 
  1=1
/*~ (when (:own params) */
  AND lic.organization IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :own
  )
/*~ ) ~*/
/*~ (when (:associated params) */
  AND (
      lic.organization IN (
      SELECT
        DISTINCT org.id
      FROM
        organization org,
        LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
      WHERE
        owners->>'userid' = :associated
    )
    OR lic.id IN (
      SELECT                 
        DISTINCT (jsonb_array_elements(w.workflowbody->'licenses'))::int As licenses
      FROM
        workflow w,
        LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        w.enabled = true
        AND w.archived = false
        AND handlers::text = :associated

      UNION ALL

      SELECT license.id
      FROM license
      INNER JOIN resource_licenses rl ON license.id = rl.licid
      INNER JOIN resource res ON rl.resid = res.id
      INNER JOIN catalogue_item item ON res.id = item.resid
      INNER JOIN workflow w ON item.wfid = w.id
      CROSS JOIN LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        w.enabled = true
        AND w.archived = false
        AND handlers::text = :associated
    )
  )
/*~ ) ~*/
;

-- :name get-license :? :1
SELECT lic.id, lic.type, lic.enabled, lic.archived, lic.organization
FROM license lic
WHERE 
  lic.id = :id
/*~ (when (:userid params) */
  AND (
    lic.organization IN (
      SELECT
        DISTINCT org.id
      FROM
        organization org,
        LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
      WHERE
        owners->>'userid' = :userid
    )
    OR lic.id IN (
      SELECT                 
        DISTINCT (jsonb_array_elements(w.workflowbody->'licenses'))::int As licenses
      FROM
        workflow w,
        LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        handlers::text = :userid
        AND w.enabled = true
        AND w.archived = false

      UNION ALL

      SELECT lic.id
      FROM license lic
      INNER JOIN resource_licenses rl ON lic.id = rl.licid
      INNER JOIN resource res ON rl.resid = res.id
      INNER JOIN catalogue_item item ON res.id = item.resid
      INNER JOIN workflow w ON item.wfid = w.id
      CROSS JOIN LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
      WHERE
        handlers::text = :userid
        AND w.enabled = true
        AND w.archived = false
    )
  )
/*~ ) ~*/
;

-- :name get-license-localizations :? :*
SELECT licid, langcode, title, textcontent, attachmentid
FROM license_localization;

-- :name get-resources-for-license :? :*
SELECT resid FROM resource_licenses WHERE licid = :id;

-- :name get-all-roles :? :*
SELECT userid, role
FROM roles;

-- :name get-roles :? :*
SELECT role
FROM roles
WHERE userId = :user;

-- :name add-role! :!
INSERT INTO roles (userId, role)
VALUES (:user, :role)
ON CONFLICT (userId, role)
DO NOTHING;

-- :name remove-role! :!
DELETE FROM roles
WHERE userId = :user
  AND role = :role;


-- :name remove-roles! :!
DELETE FROM roles
WHERE userId = :user;


-- :name add-user! :!
INSERT INTO users (userId, userAttrs)
VALUES (:user, :userattrs::jsonb)
ON CONFLICT (userId)
DO UPDATE SET userAttrs = :userattrs::jsonb;

-- :name edit-user! :!
UPDATE users
SET userAttrs = :userattrs::jsonb
WHERE userId = :user;

-- :name remove-user! :!
DELETE from users
WHERE userId = :user;

-- :name update-user-settings! :!
INSERT INTO user_settings (userId, settings)
VALUES (:user, :settings::jsonb)
ON CONFLICT (userId)
DO UPDATE SET settings = :settings::jsonb;

-- :name delete-user-settings! :!
DELETE FROM user_settings
WHERE userId = :user

-- :name get-user-secrets :? :1
SELECT secrets::TEXT
FROM user_secrets
WHERE userId = :user;

-- :name update-user-secrets! :!
INSERT INTO user_secrets (userId, secrets)
VALUES (:user, :secrets::jsonb)
ON CONFLICT (userId)
DO UPDATE SET secrets = :secrets::jsonb;

-- :name delete-user-secrets! :!
DELETE FROM user_secrets
WHERE userId = :user

-- :name get-users :? :*
SELECT userId
FROM users;

-- :name get-users-with-role :? :*
SELECT userid
FROM roles
WHERE role = :role;

-- :name get-user-attributes :? :1
SELECT userAttrs::TEXT
FROM users
WHERE userId = :user;

-- :name get-user-settings :? :1
SELECT settings::TEXT
from user_settings
where userId = :user;

-- :name get-application-events :? :*
SELECT id, eventdata::TEXT
FROM application_event
WHERE 1=1
/*~ (when (:application params) */
  AND appId = :application
/*~ ) ~*/
ORDER BY id ASC;

-- :name get-application-event :? :*
SELECT id, eventdata::TEXT
FROM application_event
WHERE id = :id;


-- :name get-application-events-since :? :*
SELECT id, eventdata::TEXT
FROM application_event
WHERE id > :id
ORDER BY id ASC;

-- :name get-latest-application-event :? :1
SELECT id, eventdata::TEXT
FROM application_event
ORDER BY id DESC
LIMIT 1;

-- :name add-application-event! :returning-execute :1
INSERT INTO application_event (appId, eventData)
VALUES (:application, :eventdata::jsonb)
RETURNING id, eventData::TEXT;

-- :name update-application-event! :!
UPDATE application_event
SET (appId, eventData) = (:application, :eventdata::jsonb)
WHERE id = :id;

-- :name replace-application-event! :returning-execute :1
UPDATE application_event
SET (id, appId, eventData) = (DEFAULT, :application, :eventdata::jsonb)
WHERE id = :id
RETURNING id, eventData::TEXT;

-- :name delete-application-events! :!
DELETE FROM application_event
WHERE appId = :application;

-- :name upsert-api-key! :insert
INSERT INTO api_key (apiKey, comment, users, paths)
VALUES (
:apikey,
:comment,
:users::jsonb,
:paths::jsonb
)
ON CONFLICT (apiKey)
DO UPDATE
SET (apiKey, comment, users, paths) = (:apikey, :comment, :users::jsonb, :paths::jsonb);

-- :name delete-api-key! :!
DELETE FROM api_key WHERE apiKey = :apikey;

-- :name get-api-key :? :1
SELECT apiKey, comment, users::TEXT, paths::TEXT FROM api_key
WHERE apiKey = :apikey;

-- :name get-api-keys :? :*
SELECT apiKey, comment, users::TEXT, paths::TEXT FROM api_key;

-- :name get-application-by-invitation-token :? :1
SELECT app.id
FROM catalogue_item_application app
JOIN application_event evt ON (app.id = evt.appid)
WHERE evt.eventdata->>'invitation/token' = :token;

-- :name get-external-ids :? :*
SELECT prefix, suffix FROM external_application_id
/*~ (when (:prefix params) */
WHERE prefix = :prefix
/*~ ) ~*/
;

-- :name add-external-id! :!
INSERT INTO external_application_id (prefix, suffix)
VALUES (:prefix, :suffix);

-- :name add-blacklist-event! :!
INSERT INTO blacklist_event (eventdata)
VALUES (:eventdata::jsonb);

-- :name update-blacklist-event! :!
UPDATE blacklist_event
SET eventdata = :eventdata::jsonb
WHERE id = :id;

-- :name get-blacklist-events :? :*
SELECT id as "event/id", eventdata::text FROM blacklist_event
WHERE 1=1
/*~ (when (:resource/ext-id params) */
  AND eventdata->>'resource/ext-id' = :resource/ext-id
/*~ ) ~*/
/*~ (when (:requester params) */
  AND eventdata->>'resource/ext-id' IN (
    SELECT
      res.resid
    FROM resource res
    WHERE 
      res.organization IN (
        SELECT
          DISTINCT org.id
        FROM
          organization org,
          LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
        WHERE
          owners->>'userid' = :requester

        UNION ALL

        SELECT
          DISTINCT org.id
        FROM
          workflow w,
          organization org,
          LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
        WHERE
          handlers::text = :requester
          AND w.organization = org.id
          AND w.enabled = true
          AND w.archived = false
      )
  )
/*~ ) ~*/
/*~ (when (:userid params) */
  AND eventdata->>'userid' = :userid
/*~ ) ~*/
ORDER BY id ASC;

-- :name put-to-outbox! :insert
INSERT INTO outbox (outboxData)
VALUES (:outboxdata::jsonb)
RETURNING id;

-- :name get-outbox :? :*
SELECT id, outboxData::text
FROM outbox
WHERE 1 = 1
/*~ (when (:ids params) */
  AND id IN (:v*:ids)
/*~ ) ~*/
ORDER BY id ASC;

-- :name update-outbox! :!
UPDATE outbox
SET outboxData = :outboxdata::jsonb
WHERE id = :id;

-- :name delete-outbox! :!
DELETE FROM outbox
WHERE id = :id;

-- :name add-to-audit-log! :!
INSERT INTO audit_log (time, path, method, apikey, userid, status)
VALUES (:time, :path, :method, :apikey, :userid, :status);

-- :name update-audit-log! :!
UPDATE audit_log
SET (time, path, method, apikey, userid, status) = (:time-new, :path-new, :method-new, :apikey-new, :userid-new, :status-new)
WHERE time = :time
--~ (if (:path params) "AND path = :path" "AND path IS NULL")
--~ (if (:method params) "AND method = :method" "AND method IS NULL")
--~ (if (:apikey params) "AND apikey = :apikey" "AND apikey IS NULL")
--~ (if (:userid params) "AND userid = :userid" "AND userid IS NULL")
--~ (if (:status params) "AND status = :status" "AND status IS NULL")
;

-- :name get-audit-log
SELECT * FROM audit_log
WHERE 1=1
/*~ (when (:userid params) */
  AND userid = :userid
/*~ ) ~*/
/*~ (when (:after params) */
  AND time >= :after
/*~ ) ~*/
/*~ (when (:before params) */
  AND time < :before
/*~ ) ~*/
/*~ (when (:path params) */
  AND path LIKE :path
/*~ ) ~*/
ORDER BY time ASC;

-- :name get-organizations :*
SELECT id, data::text as data FROM organization;

-- :name get-organization-by-id :? :1
SELECT id, data::text as data FROM organization WHERE id = :id;

-- :name add-organization! :insert
INSERT INTO organization(id, data) VALUES (:id, :data::jsonb)
ON CONFLICT (id) DO NOTHING
RETURNING id;

-- :name set-organization! :!
UPDATE organization
SET data = :data::jsonb
WHERE id = :id;

-- :name add-invitation! :insert
INSERT INTO invitation (invitationdata)
VALUES (:invitationdata::jsonb)
ON CONFLICT (id) DO NOTHING
RETURNING id;

-- :name add-comment! :insert
INSERT INTO comments (created_by,addressed_to,commenttext,appId,commentattrs)
VALUES (:userid,
        /*~ (if (:useridto params) */ :useridto /*~*/ NULL /*~ ) ~*/,
        :commenttext,
        /*~ (if (:appid params) */ :appid /*~*/ NULL /*~ ) ~*/,
        /*~ (if (:commentattrs params) */ :commentattrs::jsonb /*~*/ NULL /*~ ) ~*/
       )
ON CONFLICT (id) DO NOTHING
RETURNING id;

-- :name markread-comment! :!
UPDATE comments
SET read_at = NOW()
WHERE id = :id 
AND read_at IS NULL;

-- :name update-comment! :!
UPDATE comments
SET commentattrs = :commentattrs::jsonb
WHERE id = :id;

-- :name get-comments :? :*
SELECT id, appId as appid, created_by, addressed_to, created_at, read_at, commenttext, commentattrs::TEXT
FROM comments
WHERE 1 = 1
/*~ (when (:useridt params) */
  AND (addressed_to LIKE :useridt OR created_by LIKE :useridf)
/*~ ) ~*/
/*~ (when (:addressed_to params) */
  AND (addressed_to LIKE :addressed_to)
/*~ ) ~*/
/*~ (when (:created_by params) */
  AND (created_by LIKE :created_by)
/*~ ) ~*/
/*~ (when (:appid params) */
  AND (appId = :appid)
/*~ ) ~*/
/*~ (when (and (:markedread params) (true? (:markedread params))) */
  AND (read_at IS NOT NULL)
/*~ ) ~*/
/*~ (when (false? (:markedread params)) */
  AND (read_at IS NULL)
/*~ ) ~*/
/*~ (when (:id params) */
  AND (id = :id)
/*~ ) ~*/
ORDER BY id ASC;

-- :name get-canned-responses :? :*
SELECT r.id, r.orgId as orgid, r.response, r.title, r.created_at, r.updated_at, r.enabled,
 (SELECT json_agg(x.tag)
  FROM cannedresponsetags as x
  JOIN cannedresponse_tag_mapping as y ON (y.cannedresponseId = r.id and y.cannedresponsetagId = x.id))::text as tags
FROM cannedresponses as r
/*~ (when-not (nil? (:appid params)) */
  JOIN application_event as a ON (a.eventdata->>'event/type' LIKE 'application.event/created' and a.appid = :appid)
  JOIN catalogue_item c ON (c.id = (a.eventdata->'application/resources'->0->'catalogue-item/id')::int)
  AND c.organization = r.orgId
/*~ ) ~*/
/*~ (when (:tagid params) */
  JOIN cannedresponse_tag_mapping as t ON (t.cannedresponseId = r.id AND t.cannedresponsetagId = :tagid)
/*~ ) ~*/
WHERE 1 = 1
/*~ (when (:orgid params) */
  AND (r.orgId = :orgid)
/*~ ) ~*/
/*~ (when (:id params) */
  AND (r.id = :id)
/*~ ) ~*/
/*~ (when-not (nil? (:enabled params)) */
  AND r.enabled = :enabled
/*~ ) ~*/
/*~ (when (:userid params) */
  AND r.orgId IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :userid

    UNION ALL

    SELECT
      DISTINCT org.id
    FROM
      workflow w,
      organization org,
      LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
    WHERE
      handlers::text = :userid
      AND w.organization = org.id
      AND w.enabled = true
      AND w.archived = false
  )
/*~ ) ~*/
ORDER BY r.id ASC;

-- :name get-canned-response-tags :? :*
SELECT r.id, r.orgId as orgid, r.tag, r.created_at, r.updated_at, r.enabled,
 (SELECT json_agg(x.id)
  FROM cannedresponses as x
  JOIN cannedresponse_tag_mapping as y ON (y.cannedresponsetagId = r.id and y.cannedresponseId = x.id))::text as responses
FROM cannedresponsetags as r
/*~ (when-not (nil? (:appid params)) */
  JOIN application_event as a ON (a.eventdata->>'event/type' LIKE 'application.event/created' and a.appid = :appid)
  JOIN catalogue_item c ON (c.id = (a.eventdata->'application/resources'->0->'catalogue-item/id')::int)
  AND c.organization = r.orgId
/*~ ) ~*/
WHERE 1 = 1
/*~ (when (:orgid params) */
  AND (r.orgId = :orgid)
/*~ ) ~*/
/*~ (when (:id params) */
  AND (r.id = :id)
/*~ ) ~*/
/*~ (when-not (nil? (:enabled params)) */
  AND r.enabled = :enabled
/*~ ) ~*/
/*~ (when (:userid params) */
  AND r.orgId IN (
    SELECT
      DISTINCT org.id
    FROM
      organization org,
      LATERAL jsonb_array_elements(org.data->'organization/owners') AS owners
    WHERE
      owners->>'userid' = :userid

    UNION ALL

    SELECT
      DISTINCT org.id
    FROM
      workflow w,
      organization org,
      LATERAL jsonb_array_elements_text(w.workflowbody->'handlers') AS handlers
    WHERE
      handlers::text = :userid
      AND w.organization = org.id
      AND w.enabled = true
      AND w.archived = false
  )
/*~ ) ~*/
ORDER BY r.id ASC;

-- :name get-canned-response-mapping :? :*
SELECT id, cannedresponseId as responseid, cannedresponsetagId as tagid
FROM cannedresponse_tag_mapping
WHERE 1 = 1
/*~ (when (:id params) */
  AND (id = :id)
/*~ ) ~*/
/*~ (when (:tagid params) */
  AND (cannedresponsetagId = :tagid)
/*~ ) ~*/
/*~ (when (:responseid params) */
  AND (cannedresponseId = :responseid)
/*~ ) ~*/
ORDER BY id ASC;

-- :name add-canned-response! :insert
INSERT INTO cannedresponses (orgid,response,title)
VALUES (:orgid,:response,:title)
ON CONFLICT (id) DO NOTHING
RETURNING id;

-- :name add-canned-response-tag! :insert
INSERT INTO cannedresponsetags (orgid,tag)
VALUES (:orgid,:tag)
ON CONFLICT (id) DO NOTHING
RETURNING id;

-- :name update-canned-response! :!
UPDATE cannedresponses
SET response = :response, title = :title, updated_at = NOW()
WHERE id = :id;

-- :name update-canned-response-tag! :!
UPDATE cannedresponsetags
SET tag = :tag, updated_at = NOW()
WHERE id = :id;

-- :name tag-canned-response! :insert
INSERT INTO cannedresponse_tag_mapping (cannedresponseId,cannedresponsetagId)
VALUES (:responseid,:tagid)
ON CONFLICT (id) DO NOTHING
RETURNING id;

-- :name delete-tag-canned-response! :!
DELETE FROM cannedresponse_tag_mapping
WHERE id = :id;

-- :name set-canned-response-enabled! :!
UPDATE cannedresponses
SET enabled = :enabled
WHERE id = :id
RETURNING id;

-- :name set-canned-response-tag-enabled! :!
UPDATE cannedresponsetags
SET enabled = :enabled
WHERE id = :id
RETURNING id;

-- :name get-my-application-invitations :? :*
SELECT a.id, a.eventdata::TEXT, a2.eventdata->>'event/time' as joined, a3.eventdata->>'event/time' as removed, a4.eventdata->>'event/time' as uninvited
FROM application_event as a
JOIN users as u ON (a.eventdata->'application/member'->>'email' ILIKE u.userattrs->>'email')
LEFT JOIN application_event as a2 ON (a2.eventdata->>'event/type' LIKE 'application.event/member-joined' AND a2.appid = a.appid AND a2.eventdata->>'event/actor' LIKE u.userid and a2.id > a.id)
LEFT JOIN application_event as a3 ON (a3.eventdata->>'event/type' LIKE 'application.event/member-removed' AND a3.appid = a.appid AND a3.eventdata->'application/member'->>'userid' LIKE u.userid and a3.id > a.id)
LEFT JOIN application_event as a4 ON (a4.eventdata->>'event/type' LIKE 'application.event/member-uninvited' AND a4.appid = a.appid AND a4.eventdata->'application/member'->>'email' ILIKE u.userattrs->>'email' and a4.id > a.id)
WHERE u.userid = :userid
AND a.eventdata->>'event/type' LIKE 'application.event/member-invited'
ORDER BY a.id ASC;

-- :name get-my-invitations :? :*
SELECT i.id, i.invitationdata::TEXT
FROM invitation as i
JOIN users as u ON (i.invitationdata->>'invitation/email' ILIKE u.userattrs->>'email')
WHERE u.userid = :userid
ORDER BY i.id ASC;

-- :name get-invitations :? :*
SELECT id, invitationdata::TEXT
FROM invitation
WHERE 1 = 1
/*~ (when (:token params) */
  AND invitationdata->>'invitation/token' = :token
/*~ ) ~*/
/*~ (when (:ids params) */
  AND id IN (:v*:ids)
/*~ ) ~*/
ORDER BY id ASC;

-- :name set-invitation! :!
UPDATE invitation
SET invitationdata = :invitationdata::jsonb
WHERE id = :id;

-- :name delete-invitation! :!
DELETE FROM invitation
WHERE id = :id;

-- :name get-category-by-id :? :1
SELECT id, categorydata::TEXT
FROM category
WHERE id = :id;

-- :name get-categories :*
SELECT id, categorydata::TEXT
FROM category;

-- :name create-category! :insert
INSERT INTO category (categorydata)
VALUES (:categorydata::jsonb);

-- :name update-category! :!
UPDATE category
SET categorydata = :categorydata::jsonb
WHERE id = :id;

-- :name delete-category! :!
DELETE FROM category
WHERE id = :id;

-- :name get-user-mappings :*
SELECT extIdAttribute, extIdValue, userId
FROM user_mappings
WHERE 1 = 1
/*~ (when (:userid params) */
AND userId = :userid
/*~ ) ~*/
/*~ (when (:ext-id-attribute params) */
AND extIdAttribute = :ext-id-attribute
/*~ ) ~*/
/*~ (when (:ext-id-value params) */
AND extIdValue = :ext-id-value
/*~ ) ~*/
;

-- :name create-user-mapping! :insert
INSERT INTO user_mappings (userId, extIdAttribute, extIdValue)
VALUES (:userid, :ext-id-attribute, :ext-id-value)
ON CONFLICT (userId, extIdAttribute, extIdValue)
DO NOTHING;

-- :name delete-user-mapping! :!
DELETE FROM user_mappings
WHERE userId = :userid;

-- :name get-user-profile :? :1
SELECT userAttrs::TEXT
FROM users
WHERE userId = :userid;

-- :name get-dsr-count-for-userprofile :? :1
SELECT COUNT(*) AS dsr_count
FROM application_event
WHERE eventdata ->> 'event/actor' = :userid 
AND eventdata ->> 'event/type' = 'application.event/created';

-- :name get-dsa-count-for-userprofile :? :1
SELECT COUNT(*) AS dsa_count
FROM entitlement
WHERE userid = :userid
AND endt IS NULL 
AND revokedby IS NULL;

-- :name get-projects-count-for-userprofile :? :1
SELECT COUNT(*) AS projects_count 
FROM projects 
WHERE created_by = :userid;

-- :name get-projects :*
SELECT project_id as id, projectattrs::text as data FROM projects;

-- :name get-project-by-id :? :1
SELECT project_id as id, projectattrs::text as data FROM projects WHERE project_id = :id;

-- :name set-project! :!
UPDATE projects
SET projectattrs = :data::jsonb
WHERE project_id = :id;

-- :name add-project! :insert
INSERT INTO projects (created_by,projectattrs)
VALUES (:userid, :data::jsonb)
ON CONFLICT (project_id) DO NOTHING
RETURNING project_id as id;

-- :name edit-project! :!
UPDATE projects
SET projectattrs = :data::jsonb
WHERE project_id = :id;

-- :name remove-project! :!
DELETE from projects
WHERE project_id = :id;

-- :name get-applications-for-project! :? :*
SELECT appid as id
FROM project_application
WHERE projectid = :id;

-- :name link-project-application! :insert
INSERT INTO project_application
(appid, projectid)
VALUES
(:appid, :projectid)
RETURNING id;

-- :name unlink-project-application! :!
DELETE FROM project_application
WHERE appid = :application;

-- :name get-dashboard-dsrs-tabular-data :? :*
SELECT t.eventdata ->> 'application/id' as reqid,
t.eventdata ->> 'event/time' as createddate,
t.eventdata ->> 'event/actor' as createdby,
SPLIT_PART(t.eventdata ->> 'event/type', '/', 2) as status
FROM application_event t
INNER JOIN (
    SELECT appid, MAX(id) AS max_id
    FROM application_event
	WHERE eventdata ->> 'event/actor' = :userid
    GROUP BY appid
) grouped_t ON t.appid = grouped_t.appid 
AND t.id = grouped_t.max_id;

-- :name get-dashboard-projects-tabular-data :? :*
select project_id as projectid,
NULL as name,
created_by as createdby,
created_date as createddate,
NULL as collaborators
from projects
where created_by = :userid;

-- :name get-dashboard-dsas-tabular-data :? :*
select id as dsa_id,
NULL as name,
NULL as linked_dsr_id,
start as start_date,
endt as end_date,
approvedby as approved_by,
'approved' as status
from entitlement
where userid = :userid
and endt is NOT NULL;

-- :name get-userid-from-user-email :? :1
SELECT userid::TEXT
FROM users
WHERE LOWER(userattrs->>'email') = LOWER(:user-email-id);

-- :name get-my-trainings-count :? :1
SELECT COUNT(*) as external_training_count
FROM trainings as t
JOIN users as u on (u.userid = :userid)
WHERE LOWER(t.data->>'mail') = LOWER(u.userattrs->>'email');

-- :name get-my-trainings-details :? :*
SELECT t.organization_short_name, t.partner_platform_user_id, t.data::TEXT as data
FROM trainings as t
JOIN users as u on (u.userid = :user-id)
WHERE LOWER(t.data->>'mail') = LOWER(u.userattrs->>'email');

-- :name get-user-trainings-details :? :*
SELECT t.organization_short_name, t.partner_platform_user_id, t.data::TEXT as data
FROM trainings as t
JOIN users as u on (u.userid = :user-id)
WHERE LOWER(t.data->>'mail') = LOWER(u.userattrs->>'email')
/*~ (when (:organization-short-name params) */
  AND organization_short_name = :organization-short-name
/*~ ) ~*/;

-- :name save-user-trainings-details! :!
INSERT INTO trainings (organization_short_name, partner_platform_user_id, data)
VALUES (:organization-short-name, LOWER(:partner-platform-user-id), :data::jsonb)
ON CONFLICT (organization_short_name, partner_platform_user_id)
DO UPDATE
SET (organization_short_name, partner_platform_user_id, data) = (:organization-short-name, LOWER(:partner-platform-user-id), :data::jsonb)
RETURNING 'success' as flag;

-- :name fetch-most-recent-rg-data-of-user :? :1
SELECT u.userid::TEXT, COALESCE(rg.rg_json_data::TEXT, '') AS rg_json_data
FROM users u
LEFT JOIN research_graph_details rg 
ON LOWER(u.userid::TEXT) = LOWER(rg.userid)
AND LOWER(rg.orcid) = LOWER(:orcid)
AND rg.retrieved_at > NOW() - INTERVAL '24 hours'
WHERE LOWER(u.userattrs->>'eduPersonOrcid') = LOWER(:orcid)
ORDER BY COALESCE(rg.retrieved_at, NOW()) DESC
LIMIT 1;

-- :name insert-rg-data-of-user! :insert
INSERT INTO research_graph_details (userid, orcid, rg_json_data) 
VALUES (:userid, :orcid, :rg_json_data::jsonb)
RETURNING 'success' as flag;

-- :name get-logged-in-user-roles :? :1
SELECT role 
FROM roles 
WHERE LOWER(userid) = LOWER(:user-id);

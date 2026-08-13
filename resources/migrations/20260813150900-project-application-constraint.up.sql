WITH ordered AS ( 
  SELECT id, appid, projectid,
    rank() OVER (PARTITION BY appid, projectid ORDER BY id) AS rnk 
  FROM project_application
),
to_delete AS ( 
  SELECT id, appid, projectid
  FROM   ordered 
  WHERE  rnk > 1
) 
DELETE 
FROM project_application
USING to_delete 
WHERE project_application.id = to_delete.id;
--;;
ALTER TABLE project_application ADD CONSTRAINT project_application_uniq UNIQUE (appid,projectid);

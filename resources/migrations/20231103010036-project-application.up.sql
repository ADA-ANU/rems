CREATE TABLE project_application (
  id serial NOT NULL PRIMARY KEY,
  appId integer DEFAULT NULL,
  projectId integer DEFAULT NULL,
  CONSTRAINT project_application_ibfk_1 FOREIGN KEY (appId) REFERENCES catalogue_item_application (id),
  CONSTRAINT project_application_ibfk_2 FOREIGN KEY (projectId) REFERENCES projects (project_id)
);

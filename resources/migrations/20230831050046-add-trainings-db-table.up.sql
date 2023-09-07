CREATE TABLE trainings (
  organization_id VARCHAR(255),
  user_email VARCHAR(255),
  data JSONB,
  PRIMARY KEY (organization_id, user_email)
);
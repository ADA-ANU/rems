CREATE TABLE projects (
  project_id SERIAL PRIMARY KEY,
  created_by character varying(255) REFERENCES users(userid),
  created_date timestamp default current_timestamp
);
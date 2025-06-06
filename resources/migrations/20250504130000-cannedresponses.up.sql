CREATE TABLE cannedresponses (
  id serial NOT NULL PRIMARY KEY,
  orgId character varying(255) REFERENCES organization(id),
  response text,
  title text,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  enabled boolean default true not null
);
--;;
CREATE TABLE cannedresponsetags (
  id serial NOT NULL PRIMARY KEY,
  orgId character varying(255) REFERENCES organization(id),
  tag text,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  enabled boolean default true not null
);
--;;
CREATE TABLE cannedresponse_tag_mapping (
  id serial NOT NULL PRIMARY KEY,
  cannedresponseId integer REFERENCES cannedresponses(id),
  cannedresponsetagId integer REFERENCES cannedresponsetags(id),
  UNIQUE(cannedresponseId,cannedresponsetagId)
);

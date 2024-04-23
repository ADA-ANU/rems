CREATE TABLE comments (
  id serial NOT NULL PRIMARY KEY,
  appId integer DEFAULT NULL,
  created_by character varying(255) REFERENCES users(userid),
  addressed_to character varying(255) REFERENCES users(userid),
  created_at timestamp default current_timestamp,
  read_at timestamp DEFAULT NULL,
  commenttext text,
  commentattrs jsonb,
  CONSTRAINT comments_ibfk_1 FOREIGN KEY (appId) REFERENCES catalogue_item_application (id)
);

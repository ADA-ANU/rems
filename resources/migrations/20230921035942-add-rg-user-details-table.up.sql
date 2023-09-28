CREATE TABLE research_graph_details (
    userid varchar(255) NOT NULL,
    orcid varchar(255) NOT NULL,
    rg_json_data JSONB NOT NULL,
    retrieved_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);

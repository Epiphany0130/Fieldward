CREATE TABLE job_sources (
    id text PRIMARY KEY,
    name text NOT NULL,
    authorized boolean NOT NULL DEFAULT false,
    cursor text,
    last_verified_at timestamptz
);
CREATE TABLE jobs (
    id bigserial PRIMARY KEY,
    source_id text NOT NULL REFERENCES job_sources(id),
    source_job_id text NOT NULL,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REMOVED', 'SOURCE_CLOSED')),
    UNIQUE (source_id, source_job_id)
);
CREATE TABLE job_versions (
    id bigserial PRIMARY KEY,
    job_id bigint NOT NULL REFERENCES jobs(id),
    title text NOT NULL,
    requirements text NOT NULL,
    source_url text NOT NULL,
    verified_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX job_versions_latest ON job_versions(job_id, id DESC);

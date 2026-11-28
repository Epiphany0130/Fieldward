CREATE TABLE job_requirement_snapshots (
    job_version_id bigint PRIMARY KEY REFERENCES job_versions(id) ON DELETE CASCADE,
    status text NOT NULL CHECK (status IN ('PENDING','PROCESSING','READY','FAILED')),
    requirements jsonb,
    lease_until timestamptz,
    lease_token uuid,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'READY') = (requirements IS NOT NULL))
);

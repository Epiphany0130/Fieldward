CREATE TABLE job_applications (
    account_id bigint NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    job_id bigint NOT NULL REFERENCES jobs(id),
    favorite boolean NOT NULL DEFAULT false,
    status text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, job_id)
);
CREATE TABLE application_requests (
    account_id bigint NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    request_id text NOT NULL,
    job_id bigint NOT NULL REFERENCES jobs(id),
    status text NOT NULL,
    PRIMARY KEY (account_id, request_id)
);

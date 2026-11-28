-- Existing unconfirmed requests were never made available to users and have no
-- reviewable scope; they must not become confirmable after the upgrade.
ALTER TABLE mentor_forget_requests ADD COLUMN proposed_scope JSONB;

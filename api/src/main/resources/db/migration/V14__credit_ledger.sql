-- Provisional trial allocation; replace after Task 5 measures actual costs.
CREATE TABLE credit_allocation_config (id boolean PRIMARY KEY DEFAULT true CHECK (id), initial_points bigint NOT NULL DEFAULT 50 CHECK (initial_points >= 0));
INSERT INTO credit_allocation_config(id) VALUES (true);
CREATE TABLE credit_stage_config (stage text PRIMARY KEY, points bigint NOT NULL CHECK (points >= 0));
INSERT INTO credit_stage_config(stage, points) VALUES ('DIAGNOSIS', 10), ('SKILLS', 10);
CREATE TABLE credit_accounts (account_id bigint PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE, balance bigint NOT NULL CHECK (balance >= 0));
CREATE TABLE credit_stage_reserves (account_id bigint NOT NULL REFERENCES credit_accounts(account_id) ON DELETE CASCADE, stage text NOT NULL, points bigint NOT NULL CHECK (points >= 0), PRIMARY KEY(account_id, stage));
CREATE TABLE credit_reservations (request_id text PRIMARY KEY, account_id bigint NOT NULL REFERENCES credit_accounts(account_id) ON DELETE CASCADE, stage text NOT NULL, max_points bigint NOT NULL CHECK (max_points > 0), actual_points bigint, status text NOT NULL CHECK (status IN ('ACTIVE', 'SETTLED', 'RELEASED')));
CREATE TABLE credit_ledger (id bigserial PRIMARY KEY, account_id bigint NOT NULL REFERENCES credit_accounts(account_id) ON DELETE CASCADE, request_id text NOT NULL UNIQUE, kind text NOT NULL, points bigint NOT NULL, operator_id bigint, reason text, created_at timestamptz NOT NULL DEFAULT now());
CREATE FUNCTION credit_ledger_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF TG_OP = 'DELETE' AND pg_trigger_depth() > 1 THEN RETURN OLD; END IF; RAISE EXCEPTION 'credit ledger is immutable'; END $$;
CREATE TRIGGER credit_ledger_no_change BEFORE UPDATE OR DELETE ON credit_ledger FOR EACH ROW EXECUTE FUNCTION credit_ledger_immutable();
CREATE FUNCTION initialize_credit_account() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE initial bigint;
BEGIN
 SELECT initial_points INTO initial FROM credit_allocation_config WHERE id = true;
 INSERT INTO credit_accounts VALUES (NEW.id, initial);
 INSERT INTO credit_stage_reserves SELECT NEW.id, stage, points FROM credit_stage_config;
 IF initial > 0 THEN INSERT INTO credit_ledger(account_id, request_id, kind, points, reason) VALUES (NEW.id, 'initial:' || NEW.id, 'GRANT', initial, 'initial allocation'); END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER accounts_initialize_credits AFTER INSERT ON accounts FOR EACH ROW EXECUTE FUNCTION initialize_credit_account();
INSERT INTO credit_accounts(account_id, balance) SELECT id, 0 FROM accounts;

INSERT INTO credit_stage_reserves(account_id, stage, points)
SELECT a.id, s.stage, CASE
 WHEN s.stage = 'DIAGNOSIS' AND EXISTS (SELECT 1 FROM career_assessments ca WHERE ca.account_id = a.id AND ca.status = 'READY') THEN 0
 WHEN s.stage = 'SKILLS' AND EXISTS (SELECT 1 FROM career_skill_sessions cs WHERE cs.account_id = a.id AND cs.state->>'phase' = 'DONE') THEN 0
 ELSE s.points END
FROM accounts a CROSS JOIN credit_stage_config s;

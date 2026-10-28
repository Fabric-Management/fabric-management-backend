-- PAYMENT-PLAN-1 S4. Finance projections and evidence; no historical payment/plan backfill.
CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_payment_tenant_id ON finance.finance_payment(tenant_id,id);

-- PAYMENT REVERSAL. A reversal is a NEW payment in the opposite direction that points at the payment it cancels.
-- The original keeps its RECEIVED status, so history and already-reported period totals are never
-- rewritten: original + reversal net to zero. There is deliberately no REVERSED status.
--
--   reverses_payment_id     set on the reversal row  -> the payment it cancels
--   reversed_by_payment_id  set on the original row  -> its (single) reversal
--   reversal_reason         set on the reversal row  -> why

ALTER TABLE finance.finance_payment
    ADD COLUMN IF NOT EXISTS reverses_payment_id    UUID,
    ADD COLUMN IF NOT EXISTS reversed_by_payment_id UUID,
    ADD COLUMN IF NOT EXISTS reversal_reason        VARCHAR(1000);

-- uq_collection_payment_tenant_id (tenant_id, id), created above, is the FK target.
-- Only the reversal row has a foreign key. reversed_by_payment_id is its mirror on the original;
-- a second FK would make the pair reference each other and no single row could ever be deleted.
ALTER TABLE finance.finance_payment
    ADD CONSTRAINT fk_pay_reverses
        FOREIGN KEY (tenant_id, reverses_payment_id)
        REFERENCES finance.finance_payment (tenant_id, id),
    -- A reversal row has a reason, is never itself reversed, and never points at itself.
    ADD CONSTRAINT ck_pay_reversal_shape CHECK (
        (reverses_payment_id IS NULL AND reversal_reason IS NULL)
        OR (reverses_payment_id IS NOT NULL
            AND reverses_payment_id <> id
            AND reversed_by_payment_id IS NULL
            AND reversal_reason IS NOT NULL
            AND length(btrim(reversal_reason)) > 0)),
    ADD CONSTRAINT ck_pay_reversed_by_other CHECK (
        reversed_by_payment_id IS NULL OR reversed_by_payment_id <> id);

-- A payment is reversed at most once, and a reversal belongs to exactly one payment.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_reverses
    ON finance.finance_payment (tenant_id, reverses_payment_id)
    WHERE reverses_payment_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pay_reversed_by
    ON finance.finance_payment (tenant_id, reversed_by_payment_id)
    WHERE reversed_by_payment_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS finance.collection_projection (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  order_id uuid NOT NULL, plan_id uuid NOT NULL, revision_id uuid,
  source_version bigint NOT NULL DEFAULT 0, cancelled boolean NOT NULL DEFAULT false,
  stale boolean NOT NULL DEFAULT false, source_missing boolean NOT NULL DEFAULT false, checked_at timestamptz,
  -- What reconciliation compares with the source stamp, and when it last read / saw it change.
  -- A projection is only ever written after the source was read, so none of these is ever unknown.
  source_order_version bigint NOT NULL, source_read_at timestamptz NOT NULL, source_changed_at timestamptz NOT NULL,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,order_id)
);
COMMENT ON COLUMN finance.collection_projection.source_order_version IS
  'sales_order.version the projection was last built from';
COMMENT ON COLUMN finance.collection_projection.source_read_at IS
  'Last full read of the source order; an order is read again when this is older than its tier allows';
COMMENT ON COLUMN finance.collection_projection.source_changed_at IS
  'Last time the source differed from the projection; an order untouched for long is cold';
CREATE TABLE IF NOT EXISTS finance.collection_obligation (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  order_id uuid NOT NULL, plan_id uuid NOT NULL, logical_id uuid NOT NULL,
  partner_id uuid NOT NULL, currency varchar(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
  revision_id uuid NOT NULL, amount numeric(19,4) NOT NULL CHECK(amount>=0),
  kind varchar(20) NOT NULL CHECK(kind IN ('DOWN_PAYMENT','INSTALMENT')),
  method varchar(20) NOT NULL CHECK(method IN ('CASH','BANK_TRANSFER','CHEQUE','CREDIT_CARD','OTHER')),
  expected_date date, cheque_payment_date date, awaits_confirmation_date boolean NOT NULL DEFAULT false,
  cancelled boolean NOT NULL DEFAULT false, source_activated_at timestamptz, responsible_user_id uuid,
  financial_revision bigint NOT NULL DEFAULT 0,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,plan_id,logical_id)
);
CREATE TABLE IF NOT EXISTS finance.collection_expectation (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  obligation_id uuid NOT NULL, revision_id uuid NOT NULL, approval_id uuid NOT NULL,
  agreed_at timestamptz NOT NULL, activated_at timestamptz NOT NULL,
  amount numeric(19,4) NOT NULL CHECK(amount>=0),
  kind varchar(20) NOT NULL CHECK(kind IN ('DOWN_PAYMENT','INSTALMENT')),
  method varchar(20) NOT NULL CHECK(method IN ('CASH','BANK_TRANSFER','CHEQUE','CREDIT_CARD','OTHER')),
  expected_date date, cheque_payment_date date, awaits_confirmation_date boolean NOT NULL DEFAULT false,
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,obligation_id,revision_id),
  FOREIGN KEY(tenant_id,obligation_id) REFERENCES finance.collection_obligation(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS finance.collection_cheque (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  partner_id uuid NOT NULL, currency varchar(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
  amount numeric(19,4) NOT NULL CHECK(amount>0), received_on date NOT NULL, due_on date NOT NULL,
  instrument_reference varchar(100) NOT NULL,
  state varchar(20) NOT NULL CHECK(state IN ('RECEIVED','PRESENTED','CLEARED','DISHONOURED','RETURNED','CANCELLED')),
  evidence_revision bigint NOT NULL DEFAULT 0, cleared_payment_id uuid, last_evidence varchar(1000),
  UNIQUE(tenant_id,id),
  FOREIGN KEY(tenant_id,cleared_payment_id) REFERENCES finance.finance_payment(tenant_id,id),
  CHECK(state<>'CLEARED' OR cleared_payment_id IS NOT NULL)
);
CREATE TABLE IF NOT EXISTS finance.collection_cheque_application (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  cheque_id uuid NOT NULL, obligation_id uuid NOT NULL, amount numeric(19,4) NOT NULL CHECK(amount>0),
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,cheque_id,obligation_id),
  FOREIGN KEY(tenant_id,cheque_id) REFERENCES finance.collection_cheque(tenant_id,id),
  FOREIGN KEY(tenant_id,obligation_id) REFERENCES finance.collection_obligation(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS finance.collection_payment_match (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  obligation_id uuid NOT NULL, payment_id uuid NOT NULL, cheque_id uuid,
  amount numeric(19,4) NOT NULL CHECK(amount>0), effective_date date NOT NULL,
  occurred_at timestamptz NOT NULL, actor_id uuid NOT NULL, reason varchar(1000) NOT NULL,
  reversed_on date, reversed_at timestamptz, reversal_occurred_at timestamptz, reversed_by uuid, reversal_reason varchar(1000),
  payment_invalidated_at timestamptz, invalidation_recorded_at timestamptz,
  UNIQUE(tenant_id,id),
  FOREIGN KEY(tenant_id,obligation_id) REFERENCES finance.collection_obligation(tenant_id,id),
  FOREIGN KEY(tenant_id,payment_id) REFERENCES finance.finance_payment(tenant_id,id),
  FOREIGN KEY(tenant_id,cheque_id) REFERENCES finance.collection_cheque(tenant_id,id),
  CHECK((reversed_at IS NULL AND reversed_on IS NULL AND reversed_by IS NULL AND reversal_reason IS NULL AND reversal_occurred_at IS NULL)
     OR (reversed_at IS NOT NULL AND reversed_on IS NOT NULL AND reversed_by IS NOT NULL AND reversal_reason IS NOT NULL AND reversal_occurred_at IS NOT NULL AND reversed_on>=effective_date)),
  CHECK((payment_invalidated_at IS NULL)=(invalidation_recorded_at IS NULL))
);
CREATE TABLE IF NOT EXISTS finance.collection_cheque_event (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  cheque_id uuid NOT NULL, action varchar(20) NOT NULL CHECK(action IN ('RECEIVE','PRESENT','CLEAR','DISHONOUR','RETURN','CANCEL','CORRECT')),
  state varchar(20) NOT NULL CHECK(state IN ('RECEIVED','PRESENTED','CLEARED','DISHONOURED','RETURNED','CANCELLED')),
  effective_date date NOT NULL, occurred_at timestamptz NOT NULL, actor_id uuid NOT NULL,
  reason varchar(1000) NOT NULL, evidence varchar(1000) NOT NULL, amount numeric(19,4) NOT NULL CHECK(amount>0),
  received_on date NOT NULL, due_on date NOT NULL, instrument_reference varchar(100) NOT NULL, payment_id uuid,
  -- Execution order, independent of clock precision.
  evidence_revision bigint NOT NULL CONSTRAINT ck_collection_cheque_event_revision CHECK(evidence_revision>=0),
  UNIQUE(tenant_id,id),
  FOREIGN KEY(tenant_id,cheque_id) REFERENCES finance.collection_cheque(tenant_id,id),
  FOREIGN KEY(tenant_id,payment_id) REFERENCES finance.finance_payment(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS finance.collection_command_receipt (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  operation_id uuid NOT NULL, actor_id uuid NOT NULL, request_hash varchar(64) NOT NULL,
  resource_type varchar(30) NOT NULL CHECK(resource_type IN ('OBLIGATION','CHEQUE')),
  resource_id uuid NOT NULL, resource_version bigint NOT NULL CHECK(resource_version>=0),
  payment_id uuid, match_ids jsonb NOT NULL DEFAULT '[]',
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,operation_id),
  FOREIGN KEY(tenant_id,payment_id) REFERENCES finance.finance_payment(tenant_id,id)
);
CREATE INDEX IF NOT EXISTS idx_collection_due ON finance.collection_obligation(tenant_id,cancelled,expected_date,id);
CREATE INDEX IF NOT EXISTS idx_collection_partner ON finance.collection_obligation(tenant_id,partner_id,currency,id);
CREATE INDEX IF NOT EXISTS idx_collection_order ON finance.collection_obligation(tenant_id,order_id,id);
CREATE INDEX IF NOT EXISTS idx_collection_owner ON finance.collection_obligation(tenant_id,responsible_user_id,id);
CREATE INDEX IF NOT EXISTS idx_collection_match_payment ON finance.collection_payment_match(tenant_id,payment_id) WHERE reversed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_collection_match_obligation ON finance.collection_payment_match(tenant_id,obligation_id,created_at,id);
CREATE INDEX IF NOT EXISTS idx_collection_match_cheque ON finance.collection_payment_match(tenant_id,cheque_id);
CREATE INDEX IF NOT EXISTS idx_collection_application_obligation ON finance.collection_cheque_application(tenant_id,obligation_id,cheque_id);
CREATE INDEX IF NOT EXISTS idx_collection_cheque_due ON finance.collection_cheque(tenant_id,state,due_on,id);
CREATE INDEX IF NOT EXISTS idx_collection_cheque_reference ON finance.collection_cheque(tenant_id,partner_id,currency,instrument_reference);
CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_cheque_clearing_payment ON finance.collection_cheque(tenant_id,cleared_payment_id) WHERE cleared_payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_collection_cheque_events ON finance.collection_cheque_event(tenant_id,cheque_id,created_at,id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_cheque_event_revision ON finance.collection_cheque_event(tenant_id,cheque_id,evidence_revision);
-- Same ordering as the report's DISTINCT ON (id tie-break) so the index is usable for it.
CREATE INDEX IF NOT EXISTS idx_collection_cheque_event_temporal_order ON finance.collection_cheque_event(tenant_id,cheque_id,effective_date DESC,evidence_revision DESC,created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS idx_collection_expectation_cut ON finance.collection_expectation(tenant_id,obligation_id,activated_at DESC,created_at DESC,id DESC);
ALTER TABLE finance.collection_projection ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_projection FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_projection
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON finance.collection_projection TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON finance.collection_projection TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_obligation ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_obligation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_obligation
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON finance.collection_obligation TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON finance.collection_obligation TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_expectation ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_expectation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_expectation
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT ON finance.collection_expectation TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, DELETE ON finance.collection_expectation TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_cheque ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_cheque FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_cheque
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON finance.collection_cheque TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON finance.collection_cheque TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_cheque_application ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_cheque_application FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_cheque_application
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON finance.collection_cheque_application TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON finance.collection_cheque_application TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_payment_match ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_payment_match FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_payment_match
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE ON finance.collection_payment_match TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, UPDATE, DELETE ON finance.collection_payment_match TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_cheque_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_cheque_event FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_cheque_event
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT ON finance.collection_cheque_event TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, DELETE ON finance.collection_cheque_event TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_command_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_command_receipt FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_command_receipt
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT ON finance.collection_command_receipt TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, DELETE ON finance.collection_command_receipt TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

CREATE FUNCTION finance.guard_collection_immutable_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role boolean;
BEGIN
  SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolsuper)
    OR current_user='fabric_system'
    OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname='fabric_system' AND pg_has_role(current_user,oid,'MEMBER'))
    INTO trusted_purge_role;
  IF TG_OP='DELETE' AND trusted_purge_role
    AND current_setting('app.collection_purge_tenant',true)=OLD.tenant_id::text THEN RETURN OLD; END IF;
  RAISE EXCEPTION 'Collection evidence is append-only' USING ERRCODE='23514';
END $$;
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_expectation FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_cheque_event FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_command_receipt FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();

-- A match is evidence of cash applied to an expectation. Beyond bookkeeping columns, only two things
-- may ever be added to it, each once and never changed or taken back: its reversal, and the moment
-- its payment was seen to be void. Deleting one is allowed only by the tenant purge.
CREATE FUNCTION finance.guard_collection_match_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE trusted_purge_role boolean;
BEGIN
  IF TG_OP='DELETE' THEN
    SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolsuper)
      OR current_user='fabric_system'
      OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname='fabric_system' AND pg_has_role(current_user,oid,'MEMBER'))
      INTO trusted_purge_role;
    IF trusted_purge_role AND current_setting('app.collection_purge_tenant',true)=OLD.tenant_id::text THEN
      RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Collection evidence is append-only' USING ERRCODE='23514';
  END IF;
  IF ROW(NEW.id,NEW.uid,NEW.tenant_id,NEW.created_at,NEW.created_by,NEW.is_active,NEW.deleted_at,
         NEW.obligation_id,NEW.payment_id,NEW.cheque_id,NEW.amount,NEW.effective_date,
         NEW.occurred_at,NEW.actor_id,NEW.reason)
     IS DISTINCT FROM
     ROW(OLD.id,OLD.uid,OLD.tenant_id,OLD.created_at,OLD.created_by,OLD.is_active,OLD.deleted_at,
         OLD.obligation_id,OLD.payment_id,OLD.cheque_id,OLD.amount,OLD.effective_date,
         OLD.occurred_at,OLD.actor_id,OLD.reason) THEN
    RAISE EXCEPTION 'A collection match cannot be rewritten; reverse it instead' USING ERRCODE='23514';
  END IF;
  IF OLD.reversed_at IS NOT NULL
     AND ROW(NEW.reversed_on,NEW.reversed_at,NEW.reversal_occurred_at,NEW.reversed_by,NEW.reversal_reason)
         IS DISTINCT FROM
         ROW(OLD.reversed_on,OLD.reversed_at,OLD.reversal_occurred_at,OLD.reversed_by,OLD.reversal_reason) THEN
    RAISE EXCEPTION 'A reversed collection match cannot change' USING ERRCODE='23514';
  END IF;
  IF OLD.payment_invalidated_at IS NOT NULL
     AND ROW(NEW.payment_invalidated_at,NEW.invalidation_recorded_at)
         IS DISTINCT FROM ROW(OLD.payment_invalidated_at,OLD.invalidation_recorded_at) THEN
    RAISE EXCEPTION 'A recorded payment invalidation cannot change' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER immutable_collection_match BEFORE UPDATE OR DELETE ON finance.collection_payment_match
  FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_match_evidence();

CREATE TABLE IF NOT EXISTS finance.collection_source_observation (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
  version bigint NOT NULL DEFAULT 0, order_id uuid NOT NULL, plan_id uuid NOT NULL,
  revision_id uuid, source_version bigint NOT NULL, cancelled boolean NOT NULL,
  stale boolean NOT NULL, source_missing boolean NOT NULL, effective_at timestamptz,
  observed_at timestamptz NOT NULL, active_logical_ids jsonb NOT NULL,
  UNIQUE(tenant_id,id)
);
CREATE INDEX IF NOT EXISTS idx_collection_source_history ON finance.collection_source_observation(tenant_id,order_id,observed_at,id);
CREATE INDEX IF NOT EXISTS idx_collection_source_effective_cut ON finance.collection_source_observation(tenant_id,order_id,effective_at DESC,source_version DESC,created_at DESC);
ALTER TABLE finance.collection_source_observation ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_source_observation FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_source_observation
  USING(tenant_id=current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id=current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
 GRANT SELECT, INSERT ON finance.collection_source_observation TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
 GRANT SELECT, INSERT, DELETE ON finance.collection_source_observation TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_source_observation
 FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();

-- Finance source-discovery health is independent of the downstream FlowBoard scan.
CREATE TABLE IF NOT EXISTS finance.collection_reconciliation_health (
 id uuid PRIMARY KEY, uid varchar(100) UNIQUE, tenant_id uuid NOT NULL UNIQUE,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
 deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
 completed_at timestamptz, failed boolean NOT NULL DEFAULT false
);
ALTER TABLE finance.collection_reconciliation_health ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_reconciliation_health FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_reconciliation_health
 USING(tenant_id=current_setting('app.current_tenant', true)::uuid)
 WITH CHECK(tenant_id=current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN GRANT SELECT,INSERT,UPDATE ON finance.collection_reconciliation_health TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN GRANT SELECT,INSERT,UPDATE,DELETE ON finance.collection_reconciliation_health TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

--
-- Over-settlement resolution. When a payment plan is lowered below cash that finance has already
-- matched, finance decides what the surplus is: re-applied to another instalment of the order,
-- held as an advance, or refunded. Re-applying and holding only move matches (which are reversed,
-- never edited). A refund is cash we owe back, so it gets its own record:
--
--   collection_refund        immutable fact: this much of this inbound payment is owed back
--   collection_refund_event  append-only lifecycle: SETTLE (an outbound payment pays it),
--                            REOPEN (that payment was voided or reversed), CANCEL (not owed after all)
--
-- The state is the last event, so a past report cut can be reconstructed from effective_date and
-- created_at exactly like cheque events. A refund with no events is open.

CREATE TABLE IF NOT EXISTS finance.collection_refund (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  obligation_id uuid NOT NULL, payment_id uuid NOT NULL, partner_id uuid NOT NULL,
  currency varchar(3) NOT NULL, amount numeric(19,4) NOT NULL CHECK(amount>0),
  effective_date date NOT NULL, occurred_at timestamptz NOT NULL, actor_id uuid NOT NULL,
  reason varchar(1000) NOT NULL,
  -- Invoice allocations released in the same command so this refund could be opened: the audit link
  -- between the two. [] when the payment had enough unallocated money.
  released_allocations jsonb NOT NULL DEFAULT '[]',
  UNIQUE(tenant_id,id),
  FOREIGN KEY(tenant_id,obligation_id) REFERENCES finance.collection_obligation(tenant_id,id),
  FOREIGN KEY(tenant_id,payment_id) REFERENCES finance.finance_payment(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS finance.collection_refund_event (
  id uuid PRIMARY KEY, uid varchar(100) UNIQUE,
  tenant_id uuid NOT NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
  created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamptz, version bigint NOT NULL DEFAULT 0,
  refund_id uuid NOT NULL, step integer NOT NULL CHECK(step>0),
  action varchar(10) NOT NULL CHECK(action IN ('SETTLE','REOPEN','CANCEL')),
  effective_date date NOT NULL, occurred_at timestamptz NOT NULL, actor_id uuid NOT NULL,
  reason varchar(1000) NOT NULL, payment_id uuid,
  CHECK(action<>'SETTLE' OR payment_id IS NOT NULL),
  UNIQUE(tenant_id,id), UNIQUE(tenant_id,refund_id,step),
  FOREIGN KEY(tenant_id,refund_id) REFERENCES finance.collection_refund(tenant_id,id),
  FOREIGN KEY(tenant_id,payment_id) REFERENCES finance.finance_payment(tenant_id,id)
);
CREATE INDEX IF NOT EXISTS idx_collection_refund_obligation ON finance.collection_refund(tenant_id,obligation_id,created_at,id);
CREATE INDEX IF NOT EXISTS idx_collection_refund_payment ON finance.collection_refund(tenant_id,payment_id);
CREATE INDEX IF NOT EXISTS idx_collection_refund_event_payment ON finance.collection_refund_event(tenant_id,payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_collection_refund_event_cut ON finance.collection_refund_event(tenant_id,refund_id,effective_date DESC,step DESC);

ALTER TABLE finance.collection_refund ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_refund FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_refund
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT ON finance.collection_refund TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, DELETE ON finance.collection_refund TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE finance.collection_refund_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_refund_event FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_refund_event
  USING(tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK(tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT ON finance.collection_refund_event TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, DELETE ON finance.collection_refund_event TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

-- finance.guard_collection_immutable_evidence(), defined above: no UPDATE, and DELETE only for the
-- tenant purge.
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_refund
  FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_refund_event
  FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();

-- PAYMENT-PLAN-1 S5: no tenant members or universal reminder policy are seeded.
SET LOCAL lock_timeout='5s';
ALTER TABLE flowboard.task DROP CONSTRAINT chk_task_type;
ALTER TABLE flowboard.task ADD CONSTRAINT chk_task_type CHECK(task_type IN
 ('PLANNING','PRODUCTION','QUALITY','WAREHOUSE','SHIPMENT','APPROVAL','RECIPE_ASSIGNMENT','PROCUREMENT','COSTING','SAMPLE','RETURN','STOCK_COUNT','MAINTENANCE','ORDER_COVER','COLLECTION_FOLLOW_UP','GENERAL'));
ALTER TABLE flowboard.routing_pool DROP CONSTRAINT routing_pool_pool_key_check;
ALTER TABLE flowboard.routing_pool ADD CONSTRAINT routing_pool_pool_key_check CHECK(pool_key IN ('ORDER_COVER','COLLECTION_FOLLOW_UP'));
ALTER TABLE flowboard.routing_task_state DROP CONSTRAINT routing_task_state_pool_key_check;
ALTER TABLE flowboard.routing_task_state ADD CONSTRAINT routing_task_state_pool_key_check CHECK(pool_key IN ('ORDER_COVER','COLLECTION_FOLLOW_UP'));
ALTER TABLE flowboard.routing_failure DROP CONSTRAINT routing_failure_pool_key_check;
ALTER TABLE flowboard.routing_failure ADD CONSTRAINT routing_failure_pool_key_check CHECK(pool_key IN ('ORDER_COVER','COLLECTION_FOLLOW_UP'));
CREATE TABLE IF NOT EXISTS flowboard.collection_follow_up (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL,
 uid varchar(100) UNIQUE, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0,
 customer_label varchar(255), obligation_id uuid NOT NULL, episode integer NOT NULL CHECK(episode>0), task_id uuid,
 responsible_user_id uuid, next_follow_up_on date, promised_on date, promised_amount numeric(19,4) CHECK(promised_amount>0),
 closed_at timestamptz, closure_reason varchar(100), phase varchar(30), due_date date,
 currency varchar(3), outstanding numeric(19,4) CHECK(outstanding>=0), obligation_version bigint NOT NULL DEFAULT 0,
 source_missing boolean NOT NULL DEFAULT false, stale boolean NOT NULL DEFAULT false,
 checked_at timestamptz, activity_revision bigint NOT NULL DEFAULT 0,
 UNIQUE(tenant_id,id),UNIQUE(tenant_id,obligation_id,episode),
 FOREIGN KEY(tenant_id,task_id) REFERENCES flowboard.task(tenant_id,id),
 FOREIGN KEY(tenant_id,responsible_user_id) REFERENCES common_user.common_user(tenant_id,id),
 CHECK(phase IS NULL OR phase IN ('COLLECT_PAYMENT','RECEIVE_CHEQUE','CLEAR_CHEQUE')),
 CHECK((closed_at IS NULL)=(closure_reason IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_collection_follow_up_open ON flowboard.collection_follow_up(tenant_id,obligation_id) WHERE closed_at IS NULL AND is_active;
CREATE INDEX IF NOT EXISTS idx_collection_follow_up_due ON flowboard.collection_follow_up(tenant_id,responsible_user_id,next_follow_up_on,due_date) WHERE closed_at IS NULL;
CREATE TABLE IF NOT EXISTS flowboard.collection_contact (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL,
 uid varchar(100) UNIQUE, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0,
 follow_up_id uuid NOT NULL,actor_id uuid NOT NULL,outcome varchar(40) NOT NULL,
 occurred_at timestamptz NOT NULL,promised_on date,promised_amount numeric(19,4) CHECK(promised_amount>0),next_follow_up_on date,note varchar(2000),
 UNIQUE(tenant_id,id), FOREIGN KEY(tenant_id,follow_up_id) REFERENCES flowboard.collection_follow_up(tenant_id,id),
 CHECK(outcome IN ('REACHED','NO_ANSWER','PROMISE_TO_PAY','DISPUTED','FOLLOW_UP_SCHEDULED')),
 CHECK((outcome='PROMISE_TO_PAY')=(promised_on IS NOT NULL AND promised_amount IS NOT NULL)),
 CHECK(outcome='PROMISE_TO_PAY' OR (promised_on IS NULL AND promised_amount IS NULL)),
 CHECK(outcome<>'FOLLOW_UP_SCHEDULED' OR next_follow_up_on IS NOT NULL),
 CHECK(outcome<>'DISPUTED' OR nullif(btrim(note),'') IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_collection_contact_history ON flowboard.collection_contact(tenant_id,follow_up_id,created_at DESC,id DESC);
CREATE TABLE IF NOT EXISTS flowboard.collection_follow_up_command (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL,
 uid varchar(100) UNIQUE, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0,
 operation_id uuid NOT NULL,actor_id uuid NOT NULL,follow_up_id uuid NOT NULL,fingerprint varchar(100) NOT NULL,
 contact_id uuid,result_version bigint NOT NULL,reason varchar(1000),responsible_user_id uuid,
 UNIQUE(tenant_id,operation_id),FOREIGN KEY(tenant_id,follow_up_id) REFERENCES flowboard.collection_follow_up(tenant_id,id),
 FOREIGN KEY(tenant_id,contact_id) REFERENCES flowboard.collection_contact(tenant_id,id)
);
CREATE TABLE IF NOT EXISTS flowboard.collection_follow_up_scan (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id uuid NOT NULL,
 uid varchar(100) UNIQUE, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 created_by uuid, updated_by uuid, is_active boolean NOT NULL DEFAULT true, deleted_at timestamptz,
 version bigint NOT NULL DEFAULT 0,completed_at timestamptz,failed boolean NOT NULL DEFAULT false,UNIQUE(tenant_id));
ALTER TABLE flowboard.collection_follow_up ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.collection_follow_up FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.collection_follow_up
 USING(tenant_id=current_setting('app.current_tenant', true)::uuid)
 WITH CHECK(tenant_id=current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN GRANT SELECT,INSERT,UPDATE ON flowboard.collection_follow_up TO fabric_app;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN GRANT SELECT, INSERT, UPDATE, DELETE ON flowboard.collection_follow_up TO fabric_system;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE flowboard.collection_contact ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.collection_contact FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.collection_contact
 USING(tenant_id=current_setting('app.current_tenant', true)::uuid)
 WITH CHECK(tenant_id=current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN GRANT SELECT,INSERT ON flowboard.collection_contact TO fabric_app;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN GRANT SELECT, INSERT, DELETE ON flowboard.collection_contact TO fabric_system;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE flowboard.collection_follow_up_command ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.collection_follow_up_command FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.collection_follow_up_command
 USING(tenant_id=current_setting('app.current_tenant', true)::uuid)
 WITH CHECK(tenant_id=current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN GRANT SELECT,INSERT ON flowboard.collection_follow_up_command TO fabric_app;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN GRANT SELECT, INSERT, DELETE ON flowboard.collection_follow_up_command TO fabric_system;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
ALTER TABLE flowboard.collection_follow_up_scan ENABLE ROW LEVEL SECURITY;
ALTER TABLE flowboard.collection_follow_up_scan FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON flowboard.collection_follow_up_scan
 USING(tenant_id=current_setting('app.current_tenant', true)::uuid)
 WITH CHECK(tenant_id=current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN GRANT SELECT,INSERT,UPDATE ON flowboard.collection_follow_up_scan TO fabric_app;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN GRANT SELECT, INSERT, UPDATE, DELETE ON flowboard.collection_follow_up_scan TO fabric_system;
 EXCEPTION WHEN undefined_object THEN NULL; END $$;
CREATE FUNCTION flowboard.guard_collection_follow_up_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND (current_user='fabric_system' OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolsuper) OR EXISTS(SELECT 1 FROM pg_roles WHERE rolname='fabric_system' AND pg_has_role(current_user,oid,'MEMBER')))
 AND current_setting('app.collection_purge_tenant',true)=OLD.tenant_id::text THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'Collection follow-up evidence is append-only' USING ERRCODE='23514';
END $$;
CREATE TRIGGER immutable_collection_follow_up BEFORE UPDATE OR DELETE ON flowboard.collection_contact FOR EACH ROW EXECUTE FUNCTION flowboard.guard_collection_follow_up_evidence();
CREATE TRIGGER immutable_collection_follow_up BEFORE UPDATE OR DELETE ON flowboard.collection_follow_up_command FOR EACH ROW EXECUTE FUNCTION flowboard.guard_collection_follow_up_evidence();

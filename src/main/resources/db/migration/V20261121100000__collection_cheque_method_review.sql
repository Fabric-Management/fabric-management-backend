SET LOCAL lock_timeout='5s';

CREATE TABLE IF NOT EXISTS finance.collection_cheque_offer (
  tenant_id uuid NOT NULL,
  cheque_id uuid NOT NULL,
  obligation_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  recorded_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, cheque_id),
  FOREIGN KEY (tenant_id, cheque_id) REFERENCES finance.collection_cheque (tenant_id, id),
  FOREIGN KEY (tenant_id, obligation_id) REFERENCES finance.collection_obligation (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS idx_collection_cheque_offer_obligation
  ON finance.collection_cheque_offer (tenant_id, obligation_id);

ALTER TABLE finance.collection_cheque_offer ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance.collection_cheque_offer FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_tenant_isolation ON finance.collection_cheque_offer
  USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
DO $$ BEGIN
  GRANT SELECT, INSERT ON finance.collection_cheque_offer TO fabric_app;
EXCEPTION WHEN undefined_object THEN NULL; END $$;
DO $$ BEGIN
  GRANT SELECT, INSERT, DELETE ON finance.collection_cheque_offer TO fabric_system;
EXCEPTION WHEN undefined_object THEN NULL; END $$;

-- Evidence like its sibling ledgers: no UPDATE, and DELETE only for the tenant purge.
CREATE TRIGGER immutable_collection_evidence BEFORE UPDATE OR DELETE ON finance.collection_cheque_offer
  FOR EACH ROW EXECUTE FUNCTION finance.guard_collection_immutable_evidence();

ALTER TABLE flowboard.collection_follow_up DROP CONSTRAINT collection_follow_up_phase_check;
ALTER TABLE flowboard.collection_follow_up ADD CONSTRAINT collection_follow_up_phase_check
  CHECK (phase IS NULL OR phase IN
    ('COLLECT_PAYMENT', 'RECEIVE_CHEQUE', 'CLEAR_CHEQUE', 'REVIEW_PAYMENT_METHOD'));

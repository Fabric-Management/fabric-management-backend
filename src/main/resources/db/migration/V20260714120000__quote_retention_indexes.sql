CREATE INDEX IF NOT EXISTS idx_quote_retention_abandoned_draft
    ON sales.quote (tenant_id, updated_at)
    WHERE status = 'DRAFT';

CREATE INDEX IF NOT EXISTS idx_quote_approval_token_retention_dead
    ON sales.quote_approval_token (tenant_id, created_at)
    WHERE status IN ('EXPIRED', 'REVOKED') AND used_at IS NULL;

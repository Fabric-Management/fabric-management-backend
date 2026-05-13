-- -----------------------------------------------------------------------------
-- User navigation preferences (one row per user per tenant).
-- Used for: sort order and hidden nav items (JSONB); upsert relies on UNIQUE (tenant_id, user_id).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS common_user.user_nav_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL,
    sort_order JSONB NOT NULL DEFAULT '[]'::jsonb,
    hidden_item_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_nav_preferences_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT fk_user_nav_preferences_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_nav_preferences_user FOREIGN KEY (user_id) REFERENCES common_user.common_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_nav_preferences_tenant ON common_user.user_nav_preferences(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_nav_preferences_tenant_user ON common_user.user_nav_preferences(tenant_id, user_id);

COMMENT ON TABLE common_user.user_nav_preferences IS 'Per-user nav preferences: sort order and hidden item IDs (JSONB). One row per (tenant, user).';

-- Rollback (commented): DROP TABLE IF EXISTS common_user.user_nav_preferences CASCADE;

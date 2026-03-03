-- ============================================================================
-- V1_1_3: Add user_work_location junction table
-- ============================================================================
-- Links users to organization addresses (company locations) they work at.
-- References common_organization_address instead of copying address data,
-- so updates to a company address propagate to all linked users.
-- ============================================================================

-- address_id is part of a composite PK in organization_address; add unique
-- constraint so it can serve as a FK target for user_work_location.
ALTER TABLE common_company.common_organization_address
    ADD CONSTRAINT uq_org_address_address_id UNIQUE (address_id);

CREATE TABLE IF NOT EXISTS common_user.common_user_work_location (
    user_id         UUID         NOT NULL,
    org_address_id  UUID         NOT NULL,
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    notes           VARCHAR(255),

    -- BaseJunctionEntity columns
    tenant_id       UUID         NOT NULL,
    uid             VARCHAR(100) UNIQUE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_user_work_location PRIMARY KEY (user_id, org_address_id),

    CONSTRAINT fk_uwl_user
        FOREIGN KEY (user_id) REFERENCES common_user.common_user (id),

    CONSTRAINT fk_uwl_org_address
        FOREIGN KEY (org_address_id) REFERENCES common_company.common_organization_address (address_id)
);

CREATE INDEX IF NOT EXISTS idx_uwl_user
    ON common_user.common_user_work_location (user_id);

CREATE INDEX IF NOT EXISTS idx_uwl_org_address
    ON common_user.common_user_work_location (org_address_id);

CREATE INDEX IF NOT EXISTS idx_uwl_tenant
    ON common_user.common_user_work_location (tenant_id);

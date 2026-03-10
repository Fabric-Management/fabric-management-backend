-- ============================================================================
-- User/Role/Department cleanup: remove DepartmentCategory, Position, add role_scope, user_type, work_location
-- ============================================================================

-- Remove DepartmentCategory — use parent department hierarchy instead
ALTER TABLE common_company.common_department DROP CONSTRAINT IF EXISTS fk_department_category;
DO $$
DECLARE constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_schema = 'common_company' AND tc.table_name = 'common_department'
      AND kcu.column_name = 'department_category_id' AND tc.constraint_type = 'FOREIGN KEY';
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_company.common_department DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
ALTER TABLE common_company.common_department DROP COLUMN IF EXISTS department_category_id;

DO $$
DECLARE constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_schema = 'common_user' AND tc.table_name = 'common_role'
      AND kcu.column_name = 'department_category_id' AND tc.constraint_type = 'FOREIGN KEY';
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_user.common_role DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
UPDATE common_user.common_role SET department_category_id = NULL WHERE department_category_id IS NOT NULL;

-- Remove Position entity and UserPosition junction
DROP TABLE IF EXISTS common_user.common_user_position CASCADE;
DROP TABLE IF EXISTS common_company.common_position CASCADE;
DO $$
DECLARE constraint_name text;
BEGIN
    SELECT tc.constraint_name INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
    WHERE tc.table_schema = 'common_user' AND tc.table_name = 'common_role'
      AND kcu.column_name = 'default_position_id' AND tc.constraint_type = 'FOREIGN KEY';
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE common_user.common_role DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Add role_scope and simplify roles
ALTER TABLE common_user.common_role
    ADD COLUMN IF NOT EXISTS role_scope VARCHAR(20) DEFAULT 'INTERNAL' NOT NULL;
UPDATE common_user.common_role SET role_scope = 'SYSTEM' WHERE role_code = 'PLATFORM_ADMIN';
UPDATE common_user.common_role SET role_scope = 'PARTNER'
    WHERE role_code IN ('PARTNER_OWNER', 'PARTNER_ACCOUNTANT', 'PARTNER_BUYER', 'PARTNER_VIEWER');
UPDATE common_user.common_role SET role_scope = 'INTERNAL' WHERE role_scope NOT IN ('SYSTEM', 'PARTNER');
UPDATE common_user.common_role SET is_active = FALSE
    WHERE role_code IN ('PROD_MANAGER', 'PROD_WORKER', 'HR_MANAGER', 'LOG_MANAGER', 'WAREHOUSE_WORKER');
CREATE INDEX IF NOT EXISTS idx_role_scope ON common_user.common_role (role_scope);

-- Add user_type to common_user
ALTER TABLE common_user.common_user
    ADD COLUMN IF NOT EXISTS user_type VARCHAR(20) DEFAULT 'INTERNAL' NOT NULL;
UPDATE common_user.common_user u SET user_type = 'EXTERNAL'
FROM common_user.common_role r WHERE u.role_id = r.id AND r.role_scope = 'PARTNER';
CREATE INDEX IF NOT EXISTS idx_user_type ON common_user.common_user (user_type);

-- Add user_work_location junction table
ALTER TABLE common_company.common_organization_address
    ADD CONSTRAINT uq_org_address_address_id UNIQUE (address_id);
CREATE TABLE IF NOT EXISTS common_user.common_user_work_location (
    user_id         UUID         NOT NULL,
    org_address_id  UUID         NOT NULL,
    is_primary     BOOLEAN      NOT NULL DEFAULT FALSE,
    notes          VARCHAR(255),
    tenant_id      UUID         NOT NULL,
    uid            VARCHAR(100) UNIQUE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_work_location PRIMARY KEY (user_id, org_address_id),
    CONSTRAINT fk_uwl_user FOREIGN KEY (user_id) REFERENCES common_user.common_user (id),
    CONSTRAINT fk_uwl_org_address FOREIGN KEY (org_address_id) REFERENCES common_company.common_organization_address (address_id)
);
CREATE INDEX IF NOT EXISTS idx_uwl_user ON common_user.common_user_work_location (user_id);
CREATE INDEX IF NOT EXISTS idx_uwl_org_address ON common_user.common_user_work_location (org_address_id);
CREATE INDEX IF NOT EXISTS idx_uwl_tenant ON common_user.common_user_work_location (tenant_id);

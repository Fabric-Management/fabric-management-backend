-- ============================================================================
-- V22: Position Architecture - Job Position Management
-- ============================================================================
-- Establishes Position entity and User-Position relationship
-- Positions are department-scoped and tenant-isolated
-- Last Updated: 2025-11-04
-- ============================================================================

-- ============================================================================
-- TABLE: common_position
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_company.common_position (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    
    department_id UUID NOT NULL,
    position_name VARCHAR(100) NOT NULL,
    position_code VARCHAR(50),
    description VARCHAR(500),
    
    -- Default role assignment (optional)
    default_role_id UUID,
    
    -- Position hierarchy (optional)
    hierarchical_parent_id UUID,
    
    display_order INTEGER DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_position_department 
        FOREIGN KEY (department_id) 
        REFERENCES common_company.common_department(id) ON DELETE CASCADE,
    CONSTRAINT fk_position_role 
        FOREIGN KEY (default_role_id) 
        REFERENCES common_company.common_role(id) ON DELETE SET NULL,
    CONSTRAINT fk_position_parent 
        FOREIGN KEY (hierarchical_parent_id) 
        REFERENCES common_company.common_position(id) ON DELETE SET NULL,
    
    CONSTRAINT uk_position_tenant_code UNIQUE(tenant_id, position_code)
);

CREATE INDEX idx_position_tenant ON common_company.common_position(tenant_id);
CREATE INDEX idx_position_department ON common_company.common_position(department_id);
CREATE INDEX idx_position_active ON common_company.common_position(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_position_display_order ON common_company.common_position(display_order);
CREATE INDEX idx_position_parent ON common_company.common_position(hierarchical_parent_id);

COMMENT ON TABLE common_company.common_position IS 'Job positions within departments (tenant-specific, department-scoped)';
COMMENT ON COLUMN common_company.common_position.department_id IS 'Department this position belongs to';
COMMENT ON COLUMN common_company.common_position.position_code IS 'Optional position code (e.g., "PROD-MGR", "YARN-OP") for system reference';
COMMENT ON COLUMN common_company.common_position.default_role_id IS 'Default role assignment for this position (optional, can be overridden per user)';
COMMENT ON COLUMN common_company.common_position.hierarchical_parent_id IS 'Parent position in organizational hierarchy (e.g., Manager → Supervisor → Operator)';
COMMENT ON COLUMN common_company.common_position.display_order IS 'Sort order for UI display within department';

-- ============================================================================
-- TABLE: common_user_position (Many-to-Many junction)
-- ============================================================================
CREATE TABLE IF NOT EXISTS common_user.common_user_position (
    user_id UUID NOT NULL,
    position_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    
    -- Position assignment history tracking
    effective_date DATE NOT NULL DEFAULT CURRENT_DATE,
    end_date DATE,
    
    PRIMARY KEY (user_id, position_id),
    
    CONSTRAINT fk_user_pos_user 
        FOREIGN KEY (user_id) 
        REFERENCES common_user.common_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_pos_position 
        FOREIGN KEY (position_id) 
        REFERENCES common_company.common_position(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_pos_user ON common_user.common_user_position(user_id);
CREATE INDEX idx_user_pos_position ON common_user.common_user_position(position_id);
CREATE INDEX idx_user_pos_primary ON common_user.common_user_position(user_id, is_primary) WHERE is_primary = TRUE;

COMMENT ON TABLE common_user.common_user_position IS 'Many-to-Many relationship between User and Position';
COMMENT ON COLUMN common_user.common_user_position.is_primary IS 'Primary position assignment (user can have multiple positions)';
COMMENT ON COLUMN common_user.common_user_position.effective_date IS 'Date when position assignment became effective (for job change history)';
COMMENT ON COLUMN common_user.common_user_position.end_date IS 'Date when position assignment ended (NULL for active assignments, enables historical tracking)';


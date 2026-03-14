-- ============================================
-- MODULE: IWM (Inventory & Warehouse Management)
-- Kaynak: V056, V058–V062, V064, V065, V068, V069, V073, V074, V084
-- + production_quality_fiber_test_result (cross-module: FIBER domain, batch_id FK nedeniyle burada)
-- Bağımlılık: V002 (production schema, prod_fiber, prod_fiber_quality_standard, prod_fiber_attribute, prod_fiber_certification), V005 (partner_trading_partner_certification, organization_certification)
-- ============================================

-- ============================================================================
-- 1. production_execution_warehouse_location (V060 + V062 + V065)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_warehouse_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    deleted_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    parent_id UUID REFERENCES production.production_execution_warehouse_location(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    storage_condition VARCHAR(50) DEFAULT 'STANDARD',
    path VARCHAR(1000),
    level INTEGER DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    barcode VARCHAR(100),
    address_id UUID,
    max_weight_kg DECIMAL(12,3),
    current_weight_kg DECIMAL(12,3) DEFAULT 0,
    max_volume_m3 DECIMAL(12,3),
    current_volume_m3 DECIMAL(12,3) DEFAULT 0,
    linked_machine_id UUID,
    CONSTRAINT uk_warehouse_location_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_wh_loc_type CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'MACHINE', 'PRODUCTION_LINE')),
    CONSTRAINT ck_wh_loc_status CHECK (status IN ('AVAILABLE', 'FULL', 'BLOCKED', 'MAINTENANCE', 'RESERVED')),
    CONSTRAINT ck_wh_loc_storage_condition CHECK (storage_condition IN ('STANDARD', 'TEMPERATURE_CONTROLLED', 'HUMIDITY_CONTROLLED', 'HAZARDOUS', 'CLEAN_ROOM'))
);

CREATE UNIQUE INDEX uq_wh_loc_tenant_barcode ON production.production_execution_warehouse_location(tenant_id, barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_wh_loc_path ON production.production_execution_warehouse_location(path);
CREATE INDEX idx_wh_loc_parent ON production.production_execution_warehouse_location(parent_id);

-- ============================================================================
-- 2. production_execution_batch (V010+V058+V060+V061+V068+V084 — son hal)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    material_id UUID NOT NULL,
    material_type VARCHAR(50) NOT NULL DEFAULT 'FIBER',
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    batch_code VARCHAR(100) NOT NULL,
    supplier_batch_code VARCHAR(100),
    quantity DECIMAL(15,3) NOT NULL,
    reserved_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    consumed_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    waste_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL,
    production_date TIMESTAMP WITH TIME ZONE,
    expiry_date TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_QC',
    location_id UUID,
    parent_batch_id UUID,
    quality_standard_id UUID,
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_exec_batch_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_exec_batch_material FOREIGN KEY (material_id) REFERENCES production.prod_fiber(id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_location FOREIGN KEY (location_id) REFERENCES production.production_execution_warehouse_location(id) ON DELETE SET NULL,
    CONSTRAINT fk_batch_parent_batch FOREIGN KEY (parent_batch_id) REFERENCES production.production_execution_batch(id) ON DELETE SET NULL,
    CONSTRAINT fk_batch_quality_standard FOREIGN KEY (quality_standard_id) REFERENCES production.prod_fiber_quality_standard(id) ON DELETE SET NULL,
    CONSTRAINT uq_batch_tenant_code UNIQUE (tenant_id, batch_code),
    CONSTRAINT uq_batch_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT ck_qty_nonneg CHECK (quantity >= 0 AND reserved_quantity >= 0 AND consumed_quantity >= 0 AND waste_quantity >= 0),
    CONSTRAINT ck_qty_bounds CHECK (reserved_quantity + consumed_quantity <= quantity),
    CONSTRAINT ck_waste_within_consumed CHECK (waste_quantity <= consumed_quantity),
    CONSTRAINT ck_batch_status_valid CHECK (status IN (
        'AVAILABLE', 'RESERVED', 'IN_PROGRESS', 'DEPLETED',
        'PENDING_QC', 'QUARANTINE', 'ON_HOLD', 'QC_REJECTED', 'RETURNED', 'DESTROYED'
    ))
);

CREATE INDEX idx_exec_batch_tenant_id ON production.production_execution_batch(tenant_id);
CREATE INDEX idx_exec_batch_material_id ON production.production_execution_batch(material_id);
CREATE INDEX idx_exec_batch_code ON production.production_execution_batch(batch_code);
CREATE INDEX idx_exec_batch_status ON production.production_execution_batch(status);
CREATE INDEX idx_exec_batch_location ON production.production_execution_batch(location_id);
CREATE INDEX idx_batch_parent_batch_id ON production.production_execution_batch(parent_batch_id) WHERE parent_batch_id IS NOT NULL;
CREATE INDEX idx_batch_quality_standard ON production.production_execution_batch(quality_standard_id);

-- ============================================================================
-- 3. production_execution_batch_lineage (V056 — parent/child → production_execution_batch)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_batch_lineage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    parent_batch_id UUID NOT NULL,
    child_batch_id UUID NOT NULL,
    consumed_quantity DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    consumption_percentage DECIMAL(5,2),
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    process_reference VARCHAR(255),
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lineage_parent_batch FOREIGN KEY (parent_batch_id) REFERENCES production.production_execution_batch(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lineage_child_batch FOREIGN KEY (child_batch_id) REFERENCES production.production_execution_batch(id) ON DELETE RESTRICT,
    CONSTRAINT uq_lineage_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_lineage_parent_child UNIQUE (parent_batch_id, child_batch_id),
    CONSTRAINT ck_lineage_no_self_ref CHECK (parent_batch_id <> child_batch_id),
    CONSTRAINT ck_lineage_qty_positive CHECK (consumed_quantity > 0),
    CONSTRAINT ck_lineage_pct_range CHECK (consumption_percentage IS NULL OR (consumption_percentage > 0 AND consumption_percentage <= 100))
);

CREATE INDEX idx_lineage_parent ON production.production_execution_batch_lineage(parent_batch_id);
CREATE INDEX idx_lineage_child ON production.production_execution_batch_lineage(child_batch_id);
CREATE INDEX idx_lineage_consumed_at ON production.production_execution_batch_lineage(consumed_at);

-- ============================================================================
-- 4. production_execution_inventory_transaction (V058+V059+V060+V062+V064)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_inventory_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    batch_id UUID NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    quantity DECIMAL(15,3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    location_id UUID,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reference_id UUID,
    reference_type VARCHAR(50),
    reason VARCHAR(255),
    reason_code VARCHAR(50),
    idempotency_key VARCHAR(255),
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inv_txn_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE RESTRICT,
    CONSTRAINT uq_inv_txn_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT ck_inv_txn_type_valid CHECK (transaction_type IN (
        'RECEIPT', 'CONSUMPTION', 'WASTE', 'ADJUSTMENT', 'TRANSFER', 'RETURN', 'SAMPLE',
        'RESERVATION', 'RESERVATION_RELEASE', 'SPLIT_OUT', 'SPLIT_IN', 'TRANSFER_OUT', 'TRANSFER_IN', 'QUALITY_TEST'
    ))
);

CREATE UNIQUE INDEX uq_inv_txn_tenant_idempotency ON production.production_execution_inventory_transaction(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_inv_txn_batch ON production.production_execution_inventory_transaction(batch_id);
CREATE INDEX idx_inv_txn_type ON production.production_execution_inventory_transaction(transaction_type);
CREATE INDEX idx_inv_txn_date ON production.production_execution_inventory_transaction(transaction_date);
CREATE INDEX idx_inv_txn_tenant_batch ON production.production_execution_inventory_transaction(tenant_id, batch_id);

-- ============================================================================
-- 5. production_execution_batch_reservation (V059 — son isim)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_batch_reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    batch_id UUID NOT NULL,
    reference_id UUID,
    reference_type VARCHAR(50) NOT NULL,
    reserved_quantity DECIMAL(15,3) NOT NULL,
    consumed_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservation_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE CASCADE,
    CONSTRAINT uq_reservation_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT ck_reservation_qty_positive CHECK (reserved_quantity > 0),
    CONSTRAINT ck_reservation_consumed_within CHECK (consumed_quantity <= reserved_quantity),
    CONSTRAINT ck_reservation_status_valid CHECK (status IN ('ACTIVE', 'PARTIALLY_CONSUMED', 'FULFILLED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_reservation_active_ref ON production.production_execution_batch_reservation(batch_id, reference_id, reference_type)
    WHERE status IN ('ACTIVE', 'PARTIALLY_CONSUMED') AND is_active = TRUE;
CREATE INDEX idx_reservation_batch ON production.production_execution_batch_reservation(batch_id);
CREATE INDEX idx_reservation_status ON production.production_execution_batch_reservation(status);

-- ============================================================================
-- 6. production_execution_inventory_balance (V064 — CQRS projection)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_inventory_balance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) NOT NULL,
    batch_id UUID NOT NULL,
    location_id UUID,
    quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    consumed_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    waste_quantity DECIMAL(15,3) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL,
    last_transaction_id UUID,
    last_transaction_date TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inv_balance_tenant_uid UNIQUE (tenant_id, uid),
    CONSTRAINT uq_inv_balance_batch_location UNIQUE (tenant_id, batch_id, location_id),
    CONSTRAINT fk_inv_balance_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_balance_location FOREIGN KEY (location_id) REFERENCES production.production_execution_warehouse_location(id) ON DELETE SET NULL
);

CREATE INDEX idx_inv_balance_batch ON production.production_execution_inventory_balance(batch_id);
CREATE INDEX idx_inv_balance_location ON production.production_execution_inventory_balance(location_id);

-- ============================================================================
-- 7. production_execution_batch_override_log (V069)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_batch_override_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL,
    from_status VARCHAR(50) NOT NULL,
    to_status VARCHAR(50) NOT NULL,
    overridden_by UUID NOT NULL,
    reason TEXT NOT NULL,
    overridden_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_override_log_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE CASCADE
);

CREATE INDEX idx_override_log_batch_id ON production.production_execution_batch_override_log(batch_id);
CREATE INDEX idx_override_log_overridden_at ON production.production_execution_batch_override_log(overridden_at);

-- ============================================================================
-- 8. production_execution_batch_certification (V073 — FK partner/org cert → V005)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_batch_certification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    batch_id UUID NOT NULL,
    certification_id UUID NOT NULL,
    scope VARCHAR(50) NOT NULL DEFAULT 'BATCH',
    partner_certification_id UUID,
    org_certification_id UUID,
    cert_number VARCHAR(100),
    valid_from DATE,
    valid_until DATE,
    certifying_body_ref VARCHAR(255),
    document_url VARCHAR(512),
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_bc_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bc_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_bc_certification FOREIGN KEY (certification_id) REFERENCES production.prod_fiber_certification(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bc_partner_cert FOREIGN KEY (partner_certification_id) REFERENCES common_company.partner_trading_partner_certification(id) ON DELETE SET NULL,
    CONSTRAINT fk_bc_org_cert FOREIGN KEY (org_certification_id) REFERENCES common_company.organization_certification(id) ON DELETE SET NULL
);

CREATE INDEX idx_bc_batch ON production.production_execution_batch_certification(batch_id);
CREATE INDEX idx_bc_certification ON production.production_execution_batch_certification(certification_id);

-- ============================================================================
-- 9. production_execution_batch_attribute (V074)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_execution_batch_attribute (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE,
    batch_id UUID NOT NULL,
    attribute_id UUID NOT NULL,
    value TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ba_tenant FOREIGN KEY (tenant_id) REFERENCES common_tenant.common_tenant(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ba_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_ba_attribute FOREIGN KEY (attribute_id) REFERENCES production.prod_fiber_attribute(id) ON DELETE RESTRICT,
    CONSTRAINT uq_ba_batch_attribute UNIQUE (batch_id, attribute_id)
);

CREATE INDEX idx_ba_batch ON production.production_execution_batch_attribute(batch_id);
CREATE INDEX idx_ba_attribute ON production.production_execution_batch_attribute(attribute_id);

-- ============================================================================
-- 10. production_quality_fiber_test_result (CROSS-MODULE: FIBER domain, batch_id FK)
--     Kaynak: V025 (CREATE), V055 (moisture, trash, approval_status), V063 (batch_id)
-- ============================================================================
CREATE TABLE IF NOT EXISTS production.production_quality_fiber_test_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    uid VARCHAR(100) UNIQUE NOT NULL,
    batch_id UUID NOT NULL,
    test_date TIMESTAMP WITH TIME ZONE NOT NULL,
    test_type VARCHAR(50) NOT NULL DEFAULT 'LABORATORY',
    fineness DOUBLE PRECISION,
    length_mm DOUBLE PRECISION,
    strength_cn_dtex DOUBLE PRECISION,
    elongation_percent DOUBLE PRECISION,
    moisture_percent DOUBLE PRECISION,
    trash_content_percent DOUBLE PRECISION,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    test_lab VARCHAR(255),
    test_standard VARCHAR(100),
    remarks TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_fiber_test_batch FOREIGN KEY (batch_id) REFERENCES production.production_execution_batch(id) ON DELETE CASCADE
);

CREATE INDEX idx_fiber_test_batch ON production.production_quality_fiber_test_result(batch_id);
CREATE INDEX idx_fiber_test_date ON production.production_quality_fiber_test_result(test_date);
CREATE INDEX idx_fiber_test_approval ON production.production_quality_fiber_test_result(approval_status);

-- [IWM] module migration tamamlandı.

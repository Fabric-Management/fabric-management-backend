-- V20260612130000__payment_aggregate.sql

-- 1. Create Sequence
CREATE SEQUENCE IF NOT EXISTS finance.payment_number_seq START WITH 1 INCREMENT BY 1;

-- 2. Create finance_payment table
CREATE TABLE IF NOT EXISTS finance.finance_payment (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    uid VARCHAR(100),
    trading_partner_id UUID NOT NULL,
    payment_number VARCHAR(50) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_date DATE NOT NULL,
    bank_reference VARCHAR(100),
    notes TEXT,
    metadata JSONB,
    CONSTRAINT uk_pay_tenant_payment_number UNIQUE (tenant_id, payment_number)
);

CREATE INDEX IF NOT EXISTS idx_pay_tenant ON finance.finance_payment(tenant_id);
CREATE INDEX IF NOT EXISTS idx_pay_trading_partner ON finance.finance_payment(trading_partner_id);
CREATE INDEX IF NOT EXISTS idx_pay_payment_date ON finance.finance_payment(payment_date);
CREATE INDEX IF NOT EXISTS idx_pay_direction ON finance.finance_payment(direction);

-- 3. Create finance_payment_allocation table
CREATE TABLE IF NOT EXISTS finance.finance_payment_allocation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    uid VARCHAR(100),
    payment_id UUID NOT NULL REFERENCES finance.finance_payment(id),
    invoice_id UUID NOT NULL REFERENCES finance.finance_invoice(id),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    allocated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_paya_tenant ON finance.finance_payment_allocation(tenant_id);
CREATE INDEX IF NOT EXISTS idx_paya_payment ON finance.finance_payment_allocation(payment_id);
CREATE INDEX IF NOT EXISTS idx_paya_invoice ON finance.finance_payment_allocation(invoice_id);

-- 4. Idempotent Backfill: Create Payments and Allocations for invoices with amount_paid > 0
-- We use a derived UUID for the payment id to ensure idempotence.
-- Gen_random_uuid() is fine for allocation since we can check if it exists via invoice_id.

DO $$ 
DECLARE
    rec RECORD;
    v_payment_id UUID;
    v_payment_number VARCHAR(50);
BEGIN
    FOR rec IN 
        SELECT id as invoice_id, tenant_id, trading_partner_id, amount_paid, currency, payment_date, invoice_number
        FROM finance.finance_invoice 
        WHERE amount_paid > 0
    LOOP
        -- Derived unique payment number for this backfill: SYS-000-PAY-<invoice_id>
        v_payment_number := 'SYS-000-PAY-' || substr(rec.invoice_id::text, 1, 8);
        
        -- Check if backfill for this invoice already ran
        IF NOT EXISTS (SELECT 1 FROM finance.finance_payment WHERE tenant_id = rec.tenant_id AND payment_number = v_payment_number) THEN
            
            v_payment_id := gen_random_uuid();
            
            -- Insert Payment
            INSERT INTO finance.finance_payment (
                id, tenant_id, version, created_at, updated_at, is_active,
                trading_partner_id, payment_number, direction, method, status, 
                amount, currency, payment_date, notes
            ) VALUES (
                v_payment_id, rec.tenant_id, 0, NOW(), NOW(), true,
                rec.trading_partner_id, v_payment_number, 'INBOUND', 'OTHER', 'RECEIVED',
                rec.amount_paid, rec.currency, COALESCE(rec.payment_date, CURRENT_DATE), 'Auto-generated backfill'
            );
            
            INSERT INTO finance.finance_payment_allocation (
                id, tenant_id, version, created_at, updated_at, is_active,
                payment_id, invoice_id, amount, currency, allocated_at
            ) VALUES (
                gen_random_uuid(), rec.tenant_id, 0, NOW(), NOW(), true,
                v_payment_id, rec.invoice_id, rec.amount_paid, rec.currency, NOW()
            );
            
        END IF;
    END LOOP;
END $$;

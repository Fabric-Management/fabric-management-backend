-- =============================================================================
-- COMPANY DUPLICATE PREVENTION
-- =============================================================================
-- Adds constraints and indexes to prevent duplicate companies

-- =============================================================================
-- DATA CLEANUP: Remove existing duplicates before adding constraints
-- =============================================================================

-- Remove duplicate companies with same tax_id (keep oldest)
DELETE FROM companies 
WHERE id IN (
    SELECT id 
    FROM (
        SELECT id, 
               ROW_NUMBER() OVER (PARTITION BY tenant_id, tax_id ORDER BY created_at) as rn
        FROM companies 
        WHERE tax_id IS NOT NULL AND tax_id != ''
    ) t 
    WHERE rn > 1
);

-- Remove duplicate companies with same registration_number (keep oldest)
DELETE FROM companies 
WHERE id IN (
    SELECT id 
    FROM (
        SELECT id, 
               ROW_NUMBER() OVER (PARTITION BY tenant_id, registration_number ORDER BY created_at) as rn
        FROM companies 
        WHERE registration_number IS NOT NULL AND registration_number != ''
    ) t 
    WHERE rn > 1
);

-- =============================================================================
-- UNIQUE CONSTRAINTS
-- =============================================================================

-- Tax ID must be unique per tenant (critical business rule)
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_companies_tenant_tax_id'
    ) THEN
        ALTER TABLE companies 
        ADD CONSTRAINT uk_companies_tenant_tax_id 
        UNIQUE (tenant_id, tax_id);
    END IF;
END $$;

-- Registration number must be unique per tenant
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_companies_tenant_registration'
    ) THEN
        ALTER TABLE companies 
        ADD CONSTRAINT uk_companies_tenant_registration 
        UNIQUE (tenant_id, registration_number);
    END IF;
END $$;

-- =============================================================================
-- FUZZY SEARCH SUPPORT
-- =============================================================================

-- Enable pg_trgm extension for similarity search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create GIN index for trigram similarity on company name
-- This enables fast fuzzy/similarity searches
CREATE INDEX IF NOT EXISTS idx_companies_name_trgm 
ON companies USING gin (name gin_trgm_ops);

-- Create GIN index for trigram similarity on legal name
CREATE INDEX IF NOT EXISTS idx_companies_legal_name_trgm 
ON companies USING gin (legal_name gin_trgm_ops);

-- =============================================================================
-- FULL TEXT SEARCH SUPPORT (Optional - for advanced search)
-- =============================================================================

-- Add tsvector column for full-text search
ALTER TABLE companies 
ADD COLUMN IF NOT EXISTS name_search_vector tsvector;

-- Create function to update search vector
CREATE OR REPLACE FUNCTION update_company_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.name_search_vector = 
        setweight(to_tsvector('simple', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(NEW.legal_name, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-update search vector
DROP TRIGGER IF EXISTS trg_update_company_search_vector ON companies;
CREATE TRIGGER trg_update_company_search_vector
    BEFORE INSERT OR UPDATE OF name, legal_name ON companies
    FOR EACH ROW
    EXECUTE FUNCTION update_company_search_vector();

-- Create GIN index on search vector for fast full-text search
CREATE INDEX IF NOT EXISTS idx_companies_search_vector 
ON companies USING gin(name_search_vector);

-- Update existing records
UPDATE companies SET name_search_vector = 
    setweight(to_tsvector('simple', COALESCE(name, '')), 'A') ||
    setweight(to_tsvector('simple', COALESCE(legal_name, '')), 'B')
WHERE name_search_vector IS NULL;

-- =============================================================================
-- HELPER FUNCTION: Find Similar Companies
-- =============================================================================

CREATE OR REPLACE FUNCTION find_similar_companies(
    p_tenant_id UUID,
    p_name VARCHAR,
    p_similarity_threshold FLOAT DEFAULT 0.3
)
RETURNS TABLE (
    id UUID,
    name VARCHAR,
    legal_name VARCHAR,
    tax_id VARCHAR,
    similarity_score FLOAT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        c.id,
        c.name,
        c.legal_name,
        c.tax_id,
        GREATEST(
            similarity(c.name, p_name),
            similarity(COALESCE(c.legal_name, ''), p_name)
        ) as sim_score
    FROM companies c
    WHERE 
        c.tenant_id = p_tenant_id
        AND c.deleted = FALSE
        AND (
            similarity(c.name, p_name) > p_similarity_threshold
            OR similarity(COALESCE(c.legal_name, ''), p_name) > p_similarity_threshold
        )
    ORDER BY sim_score DESC
    LIMIT 10;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- COMMENTS
-- =============================================================================

COMMENT ON CONSTRAINT uk_companies_tenant_tax_id ON companies IS 
'Prevents duplicate companies with same tax ID within a tenant';

COMMENT ON CONSTRAINT uk_companies_tenant_registration ON companies IS 
'Prevents duplicate companies with same registration number within a tenant';

COMMENT ON INDEX idx_companies_name_trgm IS 
'Enables fuzzy/similarity search on company name using trigrams';

COMMENT ON FUNCTION find_similar_companies IS 
'Finds companies with similar names to prevent duplicates. Uses pg_trgm similarity.';


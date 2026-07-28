ALTER TABLE sales.quote
    ADD COLUMN IF NOT EXISTS owner_resolution_reason VARCHAR(30);

UPDATE sales.quote
SET owner_resolution_reason = 'LEGACY_UNKNOWN'
WHERE owner_resolution_reason IS NULL;

ALTER TABLE sales.quote
    ALTER COLUMN owner_resolution_reason SET DEFAULT 'LEGACY_UNKNOWN',
    ALTER COLUMN owner_resolution_reason SET NOT NULL,
    ALTER COLUMN assigned_to_id DROP NOT NULL;

ALTER TABLE sales.quote
    ADD CONSTRAINT chk_quote_owner_resolution_reason
        CHECK (owner_resolution_reason IN (
            'EXPLICIT_OVERRIDE',
            'PRIMARY_ASSIGNMENT',
            'CREATOR_FALLBACK',
            'TRIAGE_REQUIRED',
            'OPTIONAL_UNASSIGNED',
            'OWNERSHIP_EXEMPT',
            'LEGACY_UNKNOWN',
            'ACQUIRER',
            'ACCOUNT_TEAM'
        ));

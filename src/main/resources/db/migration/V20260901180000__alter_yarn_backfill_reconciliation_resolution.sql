ALTER TABLE production.prod_yarn_backfill_reconciliation
    ADD COLUMN resolution_action VARCHAR(20),
    ADD COLUMN resolved_candidate JSONB,
    ADD COLUMN candidate_occurrence_count INTEGER GENERATED ALWAYS AS
        (jsonb_array_length(candidates -> 'candidates')) STORED,
    ADD CONSTRAINT ck_yarn_backfill_resolution_action
        CHECK (resolution_action IN ('CHOSEN', 'DISMISSED')),
    ADD CONSTRAINT ck_yarn_backfill_resolution_status
        CHECK (
            (status = 'OPEN' AND resolution_action IS NULL)
            OR (status = 'RESOLVED' AND resolution_action IS NOT NULL)
        ),
    ADD CONSTRAINT ck_yarn_backfill_resolved_candidate
        CHECK (
            (resolution_action = 'CHOSEN' AND resolved_candidate IS NOT NULL)
            OR (
                resolution_action IS DISTINCT FROM 'CHOSEN'
                AND resolved_candidate IS NULL
            )
        );

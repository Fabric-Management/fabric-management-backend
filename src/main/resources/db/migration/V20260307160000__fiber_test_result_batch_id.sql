-- Align FiberTestResult table with entity: column batch_id (entity) was fiber_batch_id in DB.
-- After batch generalization, the referenced table is production_execution_batch.
ALTER TABLE production.production_quality_fiber_test_result
    RENAME COLUMN fiber_batch_id TO batch_id;

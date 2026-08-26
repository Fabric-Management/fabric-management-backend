ALTER TABLE production.prod_fiber_quality_standard
  ADD COLUMN uniformity_index_min DOUBLE PRECISION,
  ADD COLUMN uniformity_index_target DOUBLE PRECISION,
  ADD COLUMN uniformity_index_max DOUBLE PRECISION,
  ADD CONSTRAINT ck_fqs_uniformity_index_range
    CHECK (
      uniformity_index_min IS NULL
      OR uniformity_index_max IS NULL
      OR uniformity_index_min <= uniformity_index_max
    );

ALTER TABLE production.production_quality_fiber_test_result
  ADD COLUMN uniformity_index DOUBLE PRECISION;

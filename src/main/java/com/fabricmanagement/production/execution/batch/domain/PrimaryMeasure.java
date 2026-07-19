package com.fabricmanagement.production.execution.batch.domain;

/** Canonical physical dimension used for batch-grain stock commitments. */
@io.swagger.v3.oas.annotations.media.Schema(name = "ProductionBatchPrimaryMeasure")
public enum PrimaryMeasure {
  LENGTH,
  WEIGHT
}

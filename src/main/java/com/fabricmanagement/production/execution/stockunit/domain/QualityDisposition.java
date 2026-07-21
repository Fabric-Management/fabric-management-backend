package com.fabricmanagement.production.execution.stockunit.domain;

/** Independent quality disposition of a physical stock unit. */
public enum QualityDisposition {
  PENDING_INSPECTION,
  RELEASED,
  QUARANTINED,
  NONCONFORMING
}

package com.fabricmanagement.production.execution.batch.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Scope of batch certification: batch-level, facility-level, or supplier-level. */
@Getter
@RequiredArgsConstructor
public enum BatchCertificationScope {
  BATCH("Batch Level"),
  FACILITY("Facility Level"),
  SUPPLIER("Supplier Level");

  private final String displayName;
}

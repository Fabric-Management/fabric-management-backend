package com.fabricmanagement.production.execution.batch.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Reason for changing a batch certification record (audit / GOTS compliance).
 *
 * <p>Used when updating an existing batch certification to record why the change was made.
 */
@Getter
@RequiredArgsConstructor
public enum BatchCertificationChangeReason {
  INITIAL("Initial entry"),
  CORRECTION("Correction of incorrect data"),
  RENEWAL("Certificate renewed / new period"),
  SCOPE_CHANGE("Scope changed (e.g. BATCH → SUPPLIER)"),
  OTHER("Other");

  private final String displayLabel;
}

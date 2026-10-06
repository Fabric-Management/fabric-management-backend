package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** What a safe-edit save did. */
@Schema(name = "SalesOrderEditOutcome", enumAsRef = true)
public enum SalesOrderEditOutcome {
  /** Changes were saved and the order version moved once. */
  APPLIED,
  /** Nothing differed from what is saved; nothing was written to the order. */
  NO_CHANGE,
  /** Nothing was saved; the conflicts were answered with a new base. */
  CONFLICT
}

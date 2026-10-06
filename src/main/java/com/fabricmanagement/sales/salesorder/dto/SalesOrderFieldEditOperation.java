package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** What a field instruction does: set the given value, or clear the field (CEDIT-02 §4.2). */
@Schema(name = "SalesOrderFieldEditOperation", enumAsRef = true)
public enum SalesOrderFieldEditOperation {
  /** Set the field to {@code value}; the value is required and never blank. */
  SET,
  /** Clear the field; {@code value} must not be sent. */
  CLEAR
}

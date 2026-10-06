package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A line operation of a safe-edit save (CEDIT-02 §4.3). Absence from the list never removes a line.
 */
@Schema(name = "SalesOrderLineEditOperation", enumAsRef = true)
public enum SalesOrderLineEditOperation {
  /** A new line with a stable client id; only SET instructions, quantity required. */
  ADD,
  /** Instructions for an existing line, by its id; its product never changes here. */
  UPDATE,
  /** Remove an existing line, by its id. */
  REMOVE
}

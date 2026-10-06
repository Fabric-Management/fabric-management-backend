package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why a key or a line could not be merged (CEDIT-02 §5.6). */
@Schema(name = "SalesOrderEditConflictReason", enumAsRef = true)
public enum SalesOrderEditConflictReason {
  /** Someone else changed this key to another value since the base. */
  CHANGED_ON_SERVER,
  /** The line was removed since the base; it is never brought back. */
  LINE_REMOVED_ON_SERVER,
  /** The line to remove was changed since the base. */
  LINE_CHANGED_ON_SERVER,
  /** The line's product was corrected since the base. */
  LINE_PRODUCT_CHANGED,
  /** The value would return what another user replaced before this base; confirm it. */
  UNCONFIRMED_REVERT,
  /** The base expired; every change is reviewed against the current order. */
  REVIEW_REQUIRED
}

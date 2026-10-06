package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** How the user decided a conflict (CEDIT-02 §5.7). */
@Schema(name = "SalesOrderEditResolutionChoice", enumAsRef = true)
public enum SalesOrderEditResolutionChoice {
  /** Keep what is saved now; send no instruction for it. */
  KEEP_CURRENT,
  /** Save my value again, unchanged. */
  USE_MINE,
  /** Save a value different from my earlier one. */
  NEW_VALUE
}

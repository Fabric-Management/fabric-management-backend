package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** The source a part of a line's open quantity is covered from (SOI K10). */
@Schema(name = "CoverPortionKind", enumAsRef = true)
public enum CoverPortionKind {
  FINISHED_STOCK,
  FROM_GREIGE,
  NEW_SUPPLY
}

package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** The Incoterms edition a delivery term is agreed under; the rules differ between editions. */
@Schema(name = "IncotermsVersion", enumAsRef = true)
public enum IncotermsVersion {
  INCOTERMS_2010,
  INCOTERMS_2020;

  /** The edition assumed when a term is agreed without naming one. */
  public static final IncotermsVersion CURRENT = INCOTERMS_2020;
}

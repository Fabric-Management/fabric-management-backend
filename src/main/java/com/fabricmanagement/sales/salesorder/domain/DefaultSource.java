package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether a delivery uses the order-level default or its own value (ADR-0014 D8). */
@Schema(name = "DefaultSource", enumAsRef = true)
public enum DefaultSource {
  ORDER_DEFAULT,
  OVERRIDE
}

package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether the customer accepts partial shipment of an order (SOI A10, R18). */
@Schema(name = "PartialDeliveryPreference", enumAsRef = true)
public enum PartialDeliveryPreference {
  /** Not asked or not answered: partial shipment is never assumed. */
  UNKNOWN,
  ALLOWED,
  TOGETHER
}

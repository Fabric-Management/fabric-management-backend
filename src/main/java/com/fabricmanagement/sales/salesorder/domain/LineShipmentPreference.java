package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How a distribution's quantity may leave once goods are ready (LINE-PREFERENCES-1). It is an order
 * term the customer approves with the sent version; it does not change any stock quantity and is
 * distinct from a delivery's {@code shipComplete} and the order's {@code releaseTogether}.
 */
@Schema(
    name = "LineShipmentPreference",
    enumAsRef = true,
    description =
        "AS_READY: ready quantity may leave in parts (default). WHEN_COMPLETE: the distribution"
            + " leaves when all of it is ready; a split distribution is judged per delivery share.")
public enum LineShipmentPreference {
  AS_READY,
  WHEN_COMPLETE
}

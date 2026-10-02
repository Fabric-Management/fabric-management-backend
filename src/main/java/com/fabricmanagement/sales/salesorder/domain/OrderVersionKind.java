package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** What a version of the order was sent to the customer for. */
@Schema(name = "OrderVersionKind", enumAsRef = true)
public enum OrderVersionKind {
  /** The draft's details, for the customer's information; it cannot be approved. */
  INFORMATION,
  /** The evaluated order with planning's date, sent for the customer's approval. */
  APPROVAL
}

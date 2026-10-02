package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why a stock option may be taken for a line (SOI K08, K20). */
@Schema(name = "QuantityAcceptanceBasis", enumAsRef = true)
public enum QuantityAcceptanceBasis {
  /** The option is exactly the requested quantity; nothing changes for the customer. */
  EXACT_MATCH,
  /** The customer agreed to a quantity or piece set other than the request. */
  CUSTOMER_ACCEPTED
}

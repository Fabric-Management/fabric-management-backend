package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** What happens to the part of the request a smaller stock option leaves uncovered (SOI §4). */
@Schema(name = "RemainingNeedDecision", enumAsRef = true)
public enum RemainingNeed {
  /** The customer reduced the order to the accepted quantity. */
  REDUCED_BY_CUSTOMER,
  /** The rest stays on the line and is covered from another source (SOI D5). */
  REMAINS_OPEN
}

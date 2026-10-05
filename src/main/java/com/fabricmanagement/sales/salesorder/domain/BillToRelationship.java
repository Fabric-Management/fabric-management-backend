package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Why another legal entity is invoiced instead of the customer (ADR-0014 D4, OD-3a). */
@Schema(name = "BillToRelationship", enumAsRef = true)
public enum BillToRelationship {
  GROUP_COMPANY,
  PARENT_COMPANY,
  AGENT,
  FINANCING_PARTY,
  OTHER
}

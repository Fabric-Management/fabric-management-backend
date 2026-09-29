package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CustomerRequestDecisionOutcome", enumAsRef = true)
public enum CustomerRequestDecisionOutcome {
  APPROVED,
  REJECTED
}

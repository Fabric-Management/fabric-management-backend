package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CustomerRequestRevisionStatus", enumAsRef = true)
public enum CustomerRequestRevisionStatus {
  PROPOSED,
  SENT,
  APPROVED,
  REJECTED,
  SUPERSEDED
}

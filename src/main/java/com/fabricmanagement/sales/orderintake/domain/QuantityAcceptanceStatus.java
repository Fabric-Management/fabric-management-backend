package com.fabricmanagement.sales.orderintake.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QuantityAcceptanceStatus", enumAsRef = true)
public enum QuantityAcceptanceStatus {
  ACTIVE,
  SUPERSEDED,
  WITHDRAWN
}

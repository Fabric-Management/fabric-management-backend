package com.fabricmanagement.production.core.batch.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LotCompatibilityRequestStatus", enumAsRef = true)
public enum LotCompatibilityRequestStatus {
  OPEN,
  CONFIRMED,
  DECLINED,
  WITHDRAWN
}

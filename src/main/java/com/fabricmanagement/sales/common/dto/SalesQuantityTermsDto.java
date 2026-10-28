package com.fabricmanagement.sales.common.dto;

import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.common.domain.SalesQuantityTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(
    name = "SalesQuantityTerms",
    description = "Quantity decision, separate from physical stock and formal customer approval")
public record SalesQuantityTermsDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesQuantityPolicy policy,
    @Schema(nullable = true) UUID recordedBy,
    @Schema(nullable = true) Instant recordedAt,
    @Schema(nullable = true) String reason) {
  public static SalesQuantityTermsDto from(SalesQuantityTerms value) {
    return value == null
        ? new SalesQuantityTermsDto(SalesQuantityPolicy.UNRECORDED, null, null, null)
        : new SalesQuantityTermsDto(
            value.getPolicy(), value.getRecordedBy(), value.getRecordedAt(), value.getReason());
  }
}

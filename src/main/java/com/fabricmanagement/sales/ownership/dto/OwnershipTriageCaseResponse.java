package com.fabricmanagement.sales.ownership.dto;

import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Schema(
    name = "OwnershipTriageCaseResponse",
    description = "A customer currently requiring a commercial ownership assignment")
public record OwnershipTriageCaseResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID customerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long unassignedOpenQuoteCount,
    @Schema(nullable = true) Instant oldestUnassignedQuoteAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant gapStartedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long ageHours,
    @Schema(nullable = true) Long workAgeHours) {

  public static OwnershipTriageCaseResponse from(OwnershipTriageCase triageCase, Instant now) {
    return new OwnershipTriageCaseResponse(
        triageCase.customerId(),
        triageCase.customerName(),
        triageCase.unassignedOpenQuoteCount(),
        triageCase.oldestUnassignedQuoteAt(),
        triageCase.gapStartedAt(),
        elapsedHours(triageCase.gapStartedAt(), now),
        triageCase.oldestUnassignedQuoteAt() == null
            ? null
            : elapsedHours(triageCase.oldestUnassignedQuoteAt(), now));
  }

  private static long elapsedHours(Instant startedAt, Instant now) {
    return Math.max(0, Duration.between(startedAt, now).toHours());
  }
}

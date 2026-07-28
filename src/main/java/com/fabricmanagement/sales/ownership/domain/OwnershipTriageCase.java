package com.fabricmanagement.sales.ownership.domain;

import java.time.Instant;
import java.util.UUID;

public record OwnershipTriageCase(
    UUID customerId,
    String customerName,
    long unassignedOpenQuoteCount,
    Instant oldestUnassignedQuoteAt,
    Instant gapStartedAt,
    int agingThresholdHours) {}

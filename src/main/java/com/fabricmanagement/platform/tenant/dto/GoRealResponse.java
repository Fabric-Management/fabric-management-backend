package com.fabricmanagement.platform.tenant.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GoRealResponse(
    UUID tenantId,
    boolean demoMode,
    Instant trialStartedAt,
    Instant trialEndsAt,
    Map<String, Integer> deletedRows) {}

package com.fabricmanagement.human.leave.attendance.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record LeavePolicyRequest(
    UUID tenantId,
    UUID employeeId,
    String leaveTypeCode,
    UUID leaveTypeId,
    Instant asOfDate,
    double currentBalance,
    double accrualRatePerPeriod,
    double carryOverBalance,
    double maxCarryOver,
    Instant lastAccrualAt,
    LocalDate employmentStartDate,
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
        Map<String, Object> policyAttributes,
    String policyPackCode,
    Integer policyPackVersion,
    String countryCode) {}

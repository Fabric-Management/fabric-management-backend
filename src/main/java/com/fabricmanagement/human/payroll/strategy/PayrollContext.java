package com.fabricmanagement.human.payroll.strategy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PayrollContext(
    UUID tenantId,
    UUID payRunId,
    String countryCode,
    LocalDate periodStart,
    LocalDate periodEnd,
    String policyPackCode,
    Integer policyPackVersion,
    List<UUID> employeeIds,
    Map<String, Object> policyParameters) {}

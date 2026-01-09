package com.fabricmanagement.human.leave.attendance.domain;

import java.time.Instant;

public record LeaveAccrualResult(
    String leaveTypeCode,
    double accruedAmount,
    double newBalance,
    Instant calculatedAt,
    String policyPackCode,
    Integer policyPackVersion,
    String metadata) {}

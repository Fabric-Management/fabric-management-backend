package com.fabricmanagement.human.payroll.strategy;

import java.math.BigDecimal;
import java.util.Map;

public record PayrollResult(
    Map<java.util.UUID, BigDecimal> netAmounts,
    Map<java.util.UUID, Map<String, Object>> metadata) {}

package com.fabricmanagement.human.leave.attendance.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackResolver;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackService;
import com.fabricmanagement.human.compliance.localization.app.ResolvedPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.human.leave.app.HolidayCalendarService;
import com.fabricmanagement.human.leave.app.LeaveAccrualLogService;
import com.fabricmanagement.human.leave.app.LeaveBalanceService;
import com.fabricmanagement.human.leave.app.LeaveComplianceService;
import com.fabricmanagement.human.leave.app.LeaveTypeService;
import com.fabricmanagement.human.leave.attendance.domain.LeaveAccrualResult;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicy;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicyRequest;
import com.fabricmanagement.human.leave.domain.LeaveBalance;
import com.fabricmanagement.human.leave.domain.LeaveType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

  private final LeavePolicyRegistry leavePolicyRegistry;
  private final LeaveTypeService leaveTypeService;
  private final LeaveBalanceService leaveBalanceService;
  private final LeaveAccrualLogService leaveAccrualLogService;
  private final LeaveComplianceService leaveComplianceService;
  private final HrPolicyPackService hrPolicyPackService;
  private final HrPolicyPackResolver hrPolicyPackResolver;
  private final HolidayCalendarService holidayCalendarService;
  private final EmployeeService employeeService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public LeaveAccrualResult accrueLeave(
      UUID employeeId, String leaveTypeCode, String countryOverride, Instant asOfDate) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    String normalizedCode = leaveTypeCode.toUpperCase(Locale.ROOT);
    LeaveType leaveType = leaveTypeService.getActiveByCode(tenantId, normalizedCode);

    String country = resolveCountry(countryOverride, leaveType);
    ResolvedPolicyPack resolvedPack =
        hrPolicyPackResolver
            .resolve(tenantId, country)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No HR policy pack configured for country " + country));
    HrPolicyPack policyPack =
        hrPolicyPackService
            .findActiveByPackCode(tenantId, resolvedPack.packCode())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Policy pack not active for code " + resolvedPack.packCode()));
    leaveComplianceService.validateAgainstPolicyPack(leaveType, policyPack);

    LeaveBalance balance =
        leaveBalanceService.getOrCreateBalance(tenantId, employeeId, leaveType, country);
    double currentBalance = balance.getBalanceDays().doubleValue();
    double carryOverBalance = balance.getCarryOverDays().doubleValue();
    double accrualRate =
        leaveType.getDefaultAccrualRate() != null
            ? leaveType.getDefaultAccrualRate().doubleValue()
            : 0d;
    double maxCarryOver =
        leaveType.getMaxCarryOver() != null ? leaveType.getMaxCarryOver().doubleValue() : 0d;
    Instant lastAccrualAt = balance.getLastAccrualAt();
    Instant effectiveDate = asOfDate != null ? asOfDate : clock.instant();
    java.time.LocalDate hireDate =
        employeeService.getEmployeeByUserId(employeeId).map(Employee::getHireDate).orElse(null);

    Map<String, Object> leaveAttributes = parseAttributes(leaveType.getAttributes());
    leaveAttributes = enrichWithHolidayMetadata(leaveAttributes, tenantId, country, effectiveDate);
    Map<String, Object> policyPackAttributes = parsePolicyPayload(resolvedPack.resolvedPayload());
    Map<String, Object> policyAttributes =
        mergePolicyAttributes(policyPackAttributes, leaveAttributes);

    LeavePolicyRequest policyRequest =
        new LeavePolicyRequest(
            tenantId,
            employeeId,
            normalizedCode,
            leaveType.getId(),
            effectiveDate,
            currentBalance,
            accrualRate,
            carryOverBalance,
            maxCarryOver,
            lastAccrualAt,
            hireDate,
            policyAttributes,
            resolvedPack.packCode(),
            resolvedPack.packVersion(),
            country);

    LeavePolicy policy = leavePolicyRegistry.resolveForCountry(country, normalizedCode);
    LeaveAccrualResult calculated = policy.calculateAccrual(policyRequest);

    BigDecimal accrualAmount = BigDecimal.valueOf(calculated.accruedAmount());
    BigDecimal balanceAfter = BigDecimal.valueOf(calculated.newBalance());

    leaveBalanceService.updateBalance(
        balance,
        accrualAmount,
        BigDecimal.valueOf(carryOverBalance),
        policyPack.getPackCode(),
        policyPack.getPackVersion(),
        effectiveDate);

    leaveAccrualLogService.record(
        tenantId,
        employeeId,
        leaveType,
        accrualAmount,
        balanceAfter,
        resolvedPack.packCode(),
        resolvedPack.packVersion(),
        effectiveDate,
        calculated.metadata());

    log.debug(
        "Leave accrual completed: employeeId={}, leaveType={}, accrued={}, newBalance={}, policyPack={}#{}",
        employeeId,
        normalizedCode,
        calculated.accruedAmount(),
        calculated.newBalance(),
        policyPack.getPackCode(),
        policyPack.getPackVersion());

    return new LeaveAccrualResult(
        normalizedCode,
        calculated.accruedAmount(),
        calculated.newBalance(),
        effectiveDate,
        resolvedPack.packCode(),
        resolvedPack.packVersion(),
        calculated.metadata());
  }

  private Map<String, Object> parseAttributes(String attributesJson) {
    if (attributesJson == null || attributesJson.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(attributesJson, new TypeReference<>() {});
    } catch (Exception ex) {
      log.warn("Failed to parse leave type attributes: {}", ex.getMessage());
      return Collections.emptyMap();
    }
  }

  private Map<String, Object> enrichWithHolidayMetadata(
      Map<String, Object> baseAttributes, UUID tenantId, String countryCode, Instant asOfDate) {
    if (countryCode == null) {
      return baseAttributes;
    }
    int year = asOfDate.atZone(clock.getZone()).getYear();
    Optional<Object> holidays =
        holidayCalendarService
            .findForYear(tenantId, countryCode, year)
            .map(
                calendar -> {
                  try {
                    return objectMapper.readTree(calendar.getEntries());
                  } catch (Exception ex) {
                    log.warn("Failed to parse holiday calendar entries: {}", ex.getMessage());
                    return null;
                  }
                });
    if (holidays.isEmpty()) {
      return baseAttributes;
    }
    Map<String, Object> enriched =
        baseAttributes.isEmpty()
            ? new java.util.HashMap<>()
            : new java.util.HashMap<>(baseAttributes);
    enriched.put("holidayCalendar", holidays.get());
    return enriched;
  }

  private Map<String, Object> parsePolicyPayload(String payload) {
    if (payload == null || payload.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(payload, new TypeReference<>() {});
    } catch (Exception ex) {
      log.warn("Failed to parse policy pack payload: {}", ex.getMessage());
      return Collections.emptyMap();
    }
  }

  private Map<String, Object> mergePolicyAttributes(
      Map<String, Object> policyPackAttributes, Map<String, Object> leaveAttributes) {
    if (policyPackAttributes.isEmpty()) {
      return leaveAttributes;
    }
    Map<String, Object> merged = new HashMap<>(policyPackAttributes);
    if (!leaveAttributes.isEmpty()) {
      merged.putAll(leaveAttributes);
    }
    return merged;
  }

  private String resolveCountry(String countryOverride, LeaveType leaveType) {
    if (countryOverride != null && !countryOverride.isBlank()) {
      return countryOverride.toUpperCase(Locale.ROOT);
    }
    if (leaveType.getCountryCode() != null && !leaveType.getCountryCode().isBlank()) {
      return leaveType.getCountryCode().toUpperCase(Locale.ROOT);
    }
    String tenantCountry = TenantContext.getCurrentTenantCountry();
    return tenantCountry != null
        ? tenantCountry.toUpperCase(Locale.ROOT)
        : HrLocalizationConstants.GLOBAL_COUNTRY_CODE;
  }
}

package com.fabricmanagement.human.payroll.strategy;

import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalPayrollStrategy implements PayrollStrategy {

  @Override
  public boolean supports(String countryCode) {
    return true;
  }

  @Override
  public PayrollResult execute(PayrollContext context) {
    Map<UUID, BigDecimal> netAmounts = new HashMap<>();
    Map<UUID, Map<String, Object>> metadata = new HashMap<>();

    for (UUID employeeId : context.employeeIds()) {
      netAmounts.put(employeeId, BigDecimal.ZERO);
      metadata.put(
          employeeId,
          Map.of(
              "strategy",
              HrLocalizationConstants.GLOBAL_COUNTRY_CODE,
              "message",
              "Default payroll strategy – requires localization"));
    }

    return new PayrollResult(netAmounts, metadata);
  }
}

package com.fabricmanagement.human.payroll.strategy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 105)
public class FrPayrollStrategy extends EuPayrollStrategy {

  @Override
  public boolean supports(String countryCode) {
    return "FR".equalsIgnoreCase(countryCode);
  }

  @Override
  public PayrollResult execute(PayrollContext context) {
    PayrollResult base = super.execute(context);
    Map<UUID, BigDecimal> netAmounts = new java.util.HashMap<>(base.netAmounts());
    Map<UUID, Map<String, Object>> metadata = new java.util.HashMap<>(base.metadata());

    Map<String, Object> config = context.policyParameters();
    double solidarityRate = getDouble(config, "payroll.socialContributions.solidarity", 0.0);
    for (UUID employeeId : context.employeeIds()) {
      BigDecimal gross = getEmployeeGross(context, employeeId);
      BigDecimal solidarity = gross.multiply(BigDecimal.valueOf(solidarityRate));
      BigDecimal net = netAmounts.get(employeeId).subtract(solidarity);
      netAmounts.put(employeeId, net);
      Map<String, Object> enriched = new java.util.HashMap<>();
      if (base.metadata().get(employeeId) instanceof Map<?, ?> baseMap) {
        baseMap.forEach((key, value) -> enriched.put(String.valueOf(key), value));
      } else {
        enriched.put("base", base.metadata().get(employeeId));
      }
      enriched.put("solidarity", solidarity);
      metadata.put(employeeId, enriched);
    }

    return new PayrollResult(netAmounts, metadata);
  }
}

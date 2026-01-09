package com.fabricmanagement.human.payroll.strategy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 70)
public class UsPayrollStrategy implements PayrollStrategy {

  @Override
  public boolean supports(String countryCode) {
    return "US".equalsIgnoreCase(countryCode);
  }

  @Override
  public PayrollResult execute(PayrollContext context) {
    Map<UUID, BigDecimal> netAmounts = new HashMap<>();
    Map<UUID, Map<String, Object>> metadata = new HashMap<>();

    Map<String, Object> payrollConfig = context.policyParameters();
    List<Map<String, Object>> federalBrackets =
        getList(payrollConfig, "payroll.federalTax.brackets");
    double ficaRate = getDouble(payrollConfig, "payroll.fica.rate", 0.0765);

    for (UUID employeeId : context.employeeIds()) {
      BigDecimal gross = getEmployeeGross(context, employeeId);
      BigDecimal federalTax = applyProgressiveTax(gross, federalBrackets);
      BigDecimal fica = gross.multiply(BigDecimal.valueOf(ficaRate));
      BigDecimal net = gross.subtract(federalTax).subtract(fica);

      netAmounts.put(employeeId, net);
      metadata.put(
          employeeId,
          Map.of(
              "gross", gross,
              "federalTax", federalTax,
              "fica", fica));
    }

    return new PayrollResult(netAmounts, metadata);
  }

  protected BigDecimal getEmployeeGross(PayrollContext context, UUID employeeId) {
    Object value =
        context
            .policyParameters()
            .getOrDefault("employeeGross:" + employeeId, BigDecimal.valueOf(5000));
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    return BigDecimal.valueOf(5000);
  }

  protected BigDecimal applyProgressiveTax(BigDecimal gross, List<Map<String, Object>> brackets) {
    BigDecimal tax = BigDecimal.ZERO;
    if (brackets == null || brackets.isEmpty()) {
      return gross.multiply(BigDecimal.valueOf(0.22));
    }
    double remaining = gross.doubleValue();
    double lastThreshold = 0;
    for (Map<String, Object> bracket : brackets) {
      double threshold =
          ((Number) bracket.getOrDefault("threshold", Double.MAX_VALUE)).doubleValue();
      double rate = ((Number) bracket.getOrDefault("rate", 0.22)).doubleValue();
      double taxable = Math.min(remaining, threshold - lastThreshold);
      if (taxable <= 0) {
        continue;
      }
      tax = tax.add(BigDecimal.valueOf(taxable * rate));
      remaining -= taxable;
      lastThreshold = threshold;
      if (remaining <= 0) {
        break;
      }
    }
    if (remaining > 0) {
      double topRate =
          ((Number) brackets.get(brackets.size() - 1).getOrDefault("rate", 0.22)).doubleValue();
      tax = tax.add(BigDecimal.valueOf(remaining * topRate));
    }
    return tax;
  }

  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> getList(Map<String, Object> map, String path) {
    Object current = map;
    for (String segment : path.split("\\.")) {
      if (current instanceof Map<?, ?> m) {
        current = m.get(segment);
      } else {
        return List.of();
      }
    }
    if (current instanceof List<?>) {
      return (List<Map<String, Object>>) current;
    }
    return List.of();
  }

  protected double getDouble(Map<String, Object> map, String path, double defaultValue) {
    Object current = map;
    for (String segment : path.split("\\.")) {
      if (current instanceof Map<?, ?> m) {
        current = m.get(segment);
      } else {
        return defaultValue;
      }
    }
    if (current instanceof Number number) {
      return number.doubleValue();
    }
    return defaultValue;
  }
}

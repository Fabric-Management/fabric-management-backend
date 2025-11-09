package com.fabricmanagement.human.payroll.strategy;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 80)
public class TrPayrollStrategy implements PayrollStrategy {

    @Override
    public boolean supports(String countryCode) {
        return "TR".equalsIgnoreCase(countryCode);
    }

    @Override
    public PayrollResult execute(PayrollContext context) {
        Map<UUID, BigDecimal> netAmounts = new HashMap<>();
        Map<UUID, Map<String, Object>> metadata = new HashMap<>();

        Map<String, Object> payrollConfig = context.policyParameters();
        Map<String, Object> contributions = getMap(payrollConfig, "payroll.socialContributions");
        double sgkEmployeeRate = ((Number) contributions.getOrDefault("sgkEmployee", 0.14)).doubleValue();
        double incomeTaxRate = getDouble(payrollConfig, "payroll.incomeTax.rate", 0.15);

        for (UUID employeeId : context.employeeIds()) {
            BigDecimal gross = getEmployeeGross(context, employeeId);
            BigDecimal sgk = gross.multiply(BigDecimal.valueOf(sgkEmployeeRate));
            BigDecimal taxable = gross.subtract(sgk);
            BigDecimal incomeTax = taxable.multiply(BigDecimal.valueOf(incomeTaxRate));
            BigDecimal net = gross.subtract(sgk).subtract(incomeTax);

            netAmounts.put(employeeId, net);
            metadata.put(employeeId, Map.of(
                "gross", gross,
                "sgkEmployee", sgk,
                "incomeTax", incomeTax
            ));
        }

        return new PayrollResult(netAmounts, metadata);
    }

    protected BigDecimal getEmployeeGross(PayrollContext context, UUID employeeId) {
        Object value = context.policyParameters().getOrDefault("employeeGross:" + employeeId, BigDecimal.valueOf(30000));
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(30000);
    }

    protected Map<String, Object> getMap(Map<String, Object> map, String path) {
        Object current = map;
        for (String segment : path.split("\\.")) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(segment);
            } else {
                return Map.of();
            }
        }
        if (current instanceof Map<?, ?> raw) {
            Map<String, Object> converted = new HashMap<>();
            raw.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return converted;
        }
        return Map.of();
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


package com.fabricmanagement.human.payroll.strategy;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 110)
public class UkPayrollStrategy extends EuPayrollStrategy {

    @Override
    public boolean supports(String countryCode) {
        return "UK".equalsIgnoreCase(countryCode);
    }

    @Override
    public PayrollResult execute(PayrollContext context) {
        PayrollResult euResult = super.execute(context);
        Map<UUID, BigDecimal> netAmounts = new java.util.HashMap<>(euResult.netAmounts());
        Map<UUID, Map<String, Object>> metadata = new java.util.HashMap<>();

        Map<String, Object> payrollConfig = context.policyParameters();
        Map<String, Object> niConfig = getMap(payrollConfig, "payroll.nationalInsurance");
        double studentLoanRate = getDouble(payrollConfig, "payroll.studentLoan.rate", 0.09);
        double studentLoanThreshold = getDouble(payrollConfig, "payroll.studentLoan.threshold", 27295);

        for (UUID employeeId : context.employeeIds()) {
            BigDecimal gross = getEmployeeGross(context, employeeId);
            double niEmployeeRate = ((Number) niConfig.getOrDefault("employee", 0.132)).doubleValue();
            BigDecimal niContribution = gross.multiply(BigDecimal.valueOf(niEmployeeRate));
            BigDecimal studentLoan = BigDecimal.ZERO;
            if (gross.doubleValue() > studentLoanThreshold) {
                studentLoan = gross.subtract(BigDecimal.valueOf(studentLoanThreshold))
                    .multiply(BigDecimal.valueOf(studentLoanRate));
            }
            BigDecimal net = netAmounts.get(employeeId).subtract(niContribution).subtract(studentLoan);
            netAmounts.put(employeeId, net);
            Map<String, Object> enriched = new java.util.HashMap<>(euResult.metadata().getOrDefault(employeeId, Map.of()));
            enriched.put("ni", niContribution);
            enriched.put("studentLoan", studentLoan);
            metadata.put(employeeId, enriched);
        }

        return new PayrollResult(netAmounts, metadata);
    }
}


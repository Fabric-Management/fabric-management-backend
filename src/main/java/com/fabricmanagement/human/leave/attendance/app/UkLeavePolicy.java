package com.fabricmanagement.human.leave.attendance.app;

import com.fabricmanagement.human.leave.attendance.domain.LeaveAccrualResult;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicyRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 110)
public class UkLeavePolicy extends EuLeavePolicy {

    public UkLeavePolicy(Clock clock) {
        super(clock);
    }

    @Override
    public boolean supports(String countryCode, String leaveTypeCode) {
        return "UK".equalsIgnoreCase(countryCode);
    }

    @Override
    public LeaveAccrualResult calculateAccrual(LeavePolicyRequest request) {
        java.util.Map<String, Object> attributes = request.policyAttributes();
        double statutoryLeave = getDouble(attributes, "leave.annual.annualLeaveDays", 28d);
        boolean bankHolidaysIncluded = getBoolean(attributes, "leave.annual.bankHolidaysIncluded", true);
        double accrual = statutoryLeave / 12d;

        if (!bankHolidaysIncluded) {
            accrual += getDouble(attributes, "leave.annual.bankHolidayAllowance", 8d) / 12d;
        }

        accrual = applyProbation(accrual, request);
        double newBalance = request.currentBalance() + accrual;

        return new LeaveAccrualResult(
            request.leaveTypeCode(),
            accrual,
            newBalance,
            request.asOfDate(),
            request.policyPackCode(),
            request.policyPackVersion(),
            attributes.toString()
        );
    }

    private boolean getBoolean(java.util.Map<String, Object> attributes, String path, boolean defaultValue) {
        Object current = attributes;
        for (String segment : path.split("\\.")) {
            if (current instanceof java.util.Map<?, ?> map) {
                current = map.get(segment);
            } else {
                return defaultValue;
            }
        }
        if (current instanceof Boolean bool) {
            return bool;
        }
        return defaultValue;
    }
}


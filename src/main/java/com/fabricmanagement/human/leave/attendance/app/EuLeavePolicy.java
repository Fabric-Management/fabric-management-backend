package com.fabricmanagement.human.leave.attendance.app;

import com.fabricmanagement.human.leave.attendance.domain.LeaveAccrualResult;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicy;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicyRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class EuLeavePolicy implements LeavePolicy {

    private final Clock clock;

    public EuLeavePolicy(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(String countryCode, String leaveTypeCode) {
        return countryCode != null && switch (countryCode) {
            case "EU", "FR", "DE", "IT", "ES" -> true;
            default -> false;
        };
    }

    @Override
    public LeaveAccrualResult calculateAccrual(LeavePolicyRequest request) {
        double baseAnnualLeave = getDouble(request.policyAttributes(), "leave.default.annualLeaveDays", 20d);
        double carryOverDays = getDouble(request.policyAttributes(), "leave.default.carryOverDays", 10d);
        double accrual = baseAnnualLeave / 12d;

        accrual = applyProbation(accrual, request);
        double newBalance = request.currentBalance() + accrual;
        if (newBalance > carryOverDays + baseAnnualLeave) {
            newBalance = carryOverDays + baseAnnualLeave;
        }

        return new LeaveAccrualResult(
            request.leaveTypeCode(),
            accrual,
            newBalance,
            request.asOfDate(),
            request.policyPackCode(),
            request.policyPackVersion(),
            request.policyAttributes().toString()
        );
    }

    protected double applyProbation(double accrual, LeavePolicyRequest request) {
        LocalDate startDate = request.employmentStartDate();
        if (startDate != null) {
            long daysEmployeed = ChronoUnit.DAYS.between(startDate, request.asOfDate().atZone(clock.getZone()).toLocalDate());
            int probationDays = (int) getDouble(request.policyAttributes(), "leave.default.probationDays", 0);
            if (probationDays > 0 && daysEmployeed < probationDays) {
                return 0;
            }
        }
        return accrual;
    }

    protected double getDouble(java.util.Map<String, Object> attributes, String path, double defaultValue) {
        Object current = attributes;
        for (String segment : path.split("\\.")) {
            if (current instanceof java.util.Map<?, ?> map) {
                current = map.get(segment);
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


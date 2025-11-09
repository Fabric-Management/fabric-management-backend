package com.fabricmanagement.human.leave.attendance.app;

import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import com.fabricmanagement.human.leave.attendance.domain.LeaveAccrualResult;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicy;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicyRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalLeavePolicy implements LeavePolicy {

    private final Clock clock;

    public GlobalLeavePolicy(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(String countryCode, String leaveTypeCode) {
        return true;
    }

    @Override
    public LeaveAccrualResult calculateAccrual(LeavePolicyRequest request) {
        double accrued = request.accrualRatePerPeriod();
        double carryOver = request.carryOverBalance();
        if (request.maxCarryOver() > 0 && carryOver > request.maxCarryOver()) {
            carryOver = request.maxCarryOver();
        }
        double newBalance = request.currentBalance() + accrued;
        return new LeaveAccrualResult(
            request.leaveTypeCode(),
            accrued,
            newBalance,
            clock.instant(),
            HrLocalizationConstants.GLOBAL_COUNTRY_CODE,
            null,
            null
        );
    }
}


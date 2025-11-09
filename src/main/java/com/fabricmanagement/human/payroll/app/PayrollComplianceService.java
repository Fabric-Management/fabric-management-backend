package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.human.payroll.domain.PayPeriod;
import com.fabricmanagement.human.payroll.domain.PayRun;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PayrollComplianceService {

    public void validatePayRun(PayRun payRun) {
        PayPeriod period = payRun.getPayPeriod();
        if (payRun.getPolicyPackCode() == null) {
            throw new IllegalStateException("No policy pack supplied for pay period " + period.getPeriodCode());
        }
        String packCountry = period.getCountryCode();
        if (packCountry == null || packCountry.isBlank()) {
            log.warn("Pay period {} has no country code, defaulting to tenant country", period.getPeriodCode());
        }
    }
}


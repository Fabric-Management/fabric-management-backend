package com.fabricmanagement.human.payroll.strategy;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 104)
public class DePayrollStrategy extends EuPayrollStrategy {

    @Override
    public boolean supports(String countryCode) {
        return "DE".equalsIgnoreCase(countryCode);
    }
}


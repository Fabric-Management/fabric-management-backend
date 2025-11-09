package com.fabricmanagement.human.payroll.strategy;

public interface PayrollStrategy {

    boolean supports(String countryCode);

    PayrollResult execute(PayrollContext context);
}


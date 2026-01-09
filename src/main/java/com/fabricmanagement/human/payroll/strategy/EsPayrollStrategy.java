package com.fabricmanagement.human.payroll.strategy;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 103)
public class EsPayrollStrategy extends EuPayrollStrategy {

  @Override
  public boolean supports(String countryCode) {
    return "ES".equalsIgnoreCase(countryCode);
  }
}

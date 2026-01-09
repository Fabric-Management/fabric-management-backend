package com.fabricmanagement.human.payroll.app;

import com.fabricmanagement.human.compliance.localization.app.HrLocalizationService;
import com.fabricmanagement.human.payroll.strategy.PayrollStrategy;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayrollStrategyRegistry {

  private final List<PayrollStrategy> strategies;
  private final HrLocalizationService localizationService;

  public PayrollStrategy resolve() {
    String country = localizationService.currentContext().tenantCountryCode();
    return resolve(country);
  }

  public PayrollStrategy resolve(String countryCode) {
    return strategies.stream()
        .sorted(
            Comparator.comparingInt(
                strategy -> {
                  Order order = strategy.getClass().getAnnotation(Order.class);
                  return order != null ? order.value() : 0;
                }))
        .filter(strategy -> strategy.supports(countryCode))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No PayrollStrategy available for country=" + countryCode));
  }
}

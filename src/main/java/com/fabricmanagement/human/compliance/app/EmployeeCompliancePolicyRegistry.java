package com.fabricmanagement.human.compliance.app;

import com.fabricmanagement.human.compliance.domain.EmployeeCompliancePolicy;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import com.fabricmanagement.human.compliance.localization.app.HrLocalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EmployeeCompliancePolicyRegistry {

    private final List<EmployeeCompliancePolicy> policies;
    private final HrLocalizationService localizationService;

    public EmployeeCompliancePolicy resolve() {
        String country = localizationService.currentContext().tenantCountryCode();
        return resolve(country);
    }

    public EmployeeCompliancePolicy resolve(String countryCode) {
        String normalized = normalize(countryCode);
        return policies.stream()
            .sorted(Comparator.comparingInt(policy -> {
                Order order = policy.getClass().getAnnotation(Order.class);
                return order != null ? order.value() : 0;
            }))
            .filter(policy -> policy.supports(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No EmployeeCompliancePolicy available for country=" + normalized));
    }

    private String normalize(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return HrLocalizationConstants.GLOBAL_COUNTRY_CODE;
        }
        return countryCode.toUpperCase();
    }
}


package com.fabricmanagement.human.leave.attendance.app;

import com.fabricmanagement.human.compliance.localization.app.HrLocalizationService;
import com.fabricmanagement.human.leave.attendance.domain.LeavePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LeavePolicyRegistry {

    private final List<LeavePolicy> policies;
    private final HrLocalizationService localizationService;

    public LeavePolicy resolve(String leaveTypeCode) {
        String country = localizationService.currentContext().tenantCountryCode();
        return resolveForCountry(country, leaveTypeCode);
    }

public LeavePolicy resolveForCountry(String countryCode, String leaveTypeCode) {
    String country = countryCode != null ? countryCode : localizationService.currentContext().tenantCountryCode();
        return policies.stream()
            .sorted(Comparator.comparingInt(policy -> {
                Order order = policy.getClass().getAnnotation(Order.class);
                return order != null ? order.value() : 0;
            }))
            .filter(policy -> policy.supports(country, leaveTypeCode))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No LeavePolicy found for country=" + country + " leaveType=" + leaveTypeCode));
    }
}


package com.fabricmanagement.common.infrastructure.persistence;

import com.fabricmanagement.human.compliance.localization.app.HrLocalizationContext;
import com.fabricmanagement.human.compliance.localization.app.HrLocalizationService;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackResolver;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackService;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantContextCountryResolutionTest {

    private final HrLocalizationService localizationService =
        new HrLocalizationService(
            Mockito.mock(HrPolicyPackService.class),
            Mockito.mock(HrPolicyPackResolver.class),
            Clock.systemUTC());

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void fallsBackToGlobalWhenCountryMissing() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
        TenantContext.setCurrentTenantCountry(null);

        HrLocalizationContext context = localizationService.currentContext();

        assertEquals(tenantId, context.tenantId());
        assertEquals(HrLocalizationConstants.GLOBAL_COUNTRY_CODE, context.tenantCountryCode());
    }

    @Test
    void normalizesCountryCodeToUppercase() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
        TenantContext.setCurrentTenantCountry("tr");

        HrLocalizationContext context = localizationService.currentContext();

        assertEquals("TR", context.tenantCountryCode());
    }
}


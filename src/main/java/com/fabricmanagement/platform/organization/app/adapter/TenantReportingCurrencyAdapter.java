package com.fabricmanagement.platform.organization.app.adapter;

import com.fabricmanagement.common.domain.CurrencyConstants;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Adapter that resolves the tenant's reporting currency from the root Organization entity.
 *
 * <p>Uses {@code findRootOrganization(tenantId, EXTERNAL_PARTNER)} which finds the root org
 * (parentOrganizationId IS NULL) <b>excluding</b> EXTERNAL_PARTNER type — this returns the tenant's
 * own company organization (type = COMPANY), not a trading partner.
 */
@Component
@RequiredArgsConstructor
public class TenantReportingCurrencyAdapter implements TenantReportingCurrencyPort {

  private final OrganizationRepository organizationRepo;

  @Override
  @Cacheable(value = "tenantReportingCurrency", key = "#tenantId")
  public String getReportingCurrency(UUID tenantId) {
    // NB: second param is excludeType (NOT this type), so EXTERNAL_PARTNER is excluded
    // → returns the tenant's own root org (COMPANY type)
    return organizationRepo
        .findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER)
        .map(
            org ->
                org.getReportingCurrency() != null
                    ? org.getReportingCurrency()
                    : CurrencyConstants.PLATFORM_DEFAULT_CURRENCY)
        .orElse(CurrencyConstants.PLATFORM_DEFAULT_CURRENCY);
  }
}

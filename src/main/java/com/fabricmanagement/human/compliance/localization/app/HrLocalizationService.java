package com.fabricmanagement.human.compliance.localization.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationConstants;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HrLocalizationService {

  private final HrPolicyPackService policyPackService;
  private final HrPolicyPackResolver policyPackResolver;
  private final Clock clock;

  public HrLocalizationContext currentContext() {
    UUID tenantId = TenantContext.requireTenantId();
    String country = TenantContext.getCurrentTenantCountry();
    if (country == null || country.isBlank()) {
      country = HrLocalizationConstants.GLOBAL_COUNTRY_CODE;
    } else {
      country = country.toUpperCase();
    }
    return new HrLocalizationContext(tenantId, country);
  }

  public Optional<HrPolicyPack> resolveActivePack() {
    HrLocalizationContext context = currentContext();
    return resolveResolvedPack()
        .flatMap(
            resolved ->
                policyPackService.findActiveByPackCode(context.tenantId(), resolved.packCode()))
        .or(() -> policyPackService.findActivePack(context.tenantId(), context.tenantCountryCode()))
        .or(
            () ->
                policyPackService.findActivePack(
                    context.tenantId(), HrLocalizationConstants.GLOBAL_COUNTRY_CODE));
  }

  public Optional<ResolvedPolicyPack> resolveResolvedPack() {
    HrLocalizationContext context = currentContext();
    return policyPackResolver.resolve(context.tenantId(), context.tenantCountryCode());
  }

  public Instant now() {
    return clock.instant();
  }
}

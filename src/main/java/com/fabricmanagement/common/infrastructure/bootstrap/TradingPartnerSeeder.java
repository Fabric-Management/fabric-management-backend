package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerSeeder implements DataSeeder {

  private final TenantSystemService tenantService;
  private final TradingPartnerService tradingPartnerService;
  private final TransactionTemplate transactionTemplate;

  /** All expected partner tax IDs — used for granular isSeeded() verification. */
  private static final List<String> EXPECTED_TAX_IDS =
      List.of("1111111111", "2222222222", "3333333333", "4444444444", "5555555555");

  @Override
  public boolean isSeeded() {
    Optional<TenantDto> tenantOpt = tenantService.findBySlug(TenantSeeder.TENANT_SLUG);
    if (tenantOpt.isEmpty()) {
      return false;
    }

    return TenantContext.executeInTenantContext(
        tenantOpt.get().getId(),
        () -> {
          // Granular check: verify ALL 5 expected partners exist by tax ID
          List<TradingPartnerDto> existingPartners =
              tradingPartnerService.findAll(tenantOpt.get().getId());
          Set<String> existingTaxIds =
              existingPartners.stream()
                  .map(TradingPartnerDto::getTaxId)
                  .collect(Collectors.toSet());
          return EXPECTED_TAX_IDS.stream().allMatch(existingTaxIds::contains);
        });
  }

  @Override
  public void seed() {
    TenantDto tenant =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(() -> new IllegalStateException("Tenant must be seeded before Partners"));

    TenantContext.executeInTenantContext(
        tenant.getId(),
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                // Pre-load existing partners for per-record idempotency
                Set<String> existingTaxIds =
                    tradingPartnerService.findAll(tenant.getId()).stream()
                        .map(TradingPartnerDto::getTaxId)
                        .collect(Collectors.toSet());

                seedPartner(
                    "Oz Cotton Yarns Inc.",
                    "1111111111",
                    PartnerType.SUPPLIER,
                    "Oz Yarns",
                    existingTaxIds);
                seedPartner(
                    "Global Fashion Wear Corp.",
                    "2222222222",
                    PartnerType.CUSTOMER,
                    "Global Fashion",
                    existingTaxIds);
                seedPartner(
                    "South Dyeing Facilities",
                    "3333333333",
                    PartnerType.SUBCONTRACTOR,
                    "South Dyeing",
                    existingTaxIds);
                seedPartner(
                    "Fast Logistics Shipping LLC",
                    "4444444444",
                    PartnerType.SERVICE_PROVIDER,
                    "Fast Logistics",
                    existingTaxIds);
                seedPartner(
                    "Central Textile Mills Ltd.",
                    "5555555555",
                    PartnerType.BOTH,
                    "Central Textile",
                    existingTaxIds);
              });
        });
  }

  private void seedPartner(
      String name, String taxId, PartnerType type, String customName, Set<String> existingTaxIds) {
    if (existingTaxIds.contains(taxId)) {
      log.debug("Trading partner already exists (taxId={}), skipping: {}", taxId, name);
      return;
    }

    CreateTradingPartnerRequest req =
        CreateTradingPartnerRequest.builder()
            .companyName(name)
            .taxId(taxId)
            .partnerType(type)
            .customName(customName)
            .country("TR")
            .build();

    tradingPartnerService.createPartner(req);
    log.info("Created Trading Partner: {} - Type: {}", name, type);
  }

  @Override
  public int getOrder() {
    return 40;
  }
}

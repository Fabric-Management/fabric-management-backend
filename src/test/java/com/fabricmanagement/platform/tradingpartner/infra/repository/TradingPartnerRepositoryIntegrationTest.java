package com.fabricmanagement.platform.tradingpartner.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.LikePattern;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class TradingPartnerRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TradingPartnerRepository partnerRepository;
  @Autowired private TradingPartnerRegistryRepository registryRepository;
  @Autowired private TenantRepository tenantRepository;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    Tenant tenant = Tenant.create("SUX-3b Test Tenant " + suffix, "SUX3B-" + suffix);
    tenant.activate("test");
    tenantId = tenantRepository.saveAndFlush(tenant).getId();
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @Transactional
  void customerNameSearchReturnsOnlyActiveCustomerAndBothIdsWithLiteralMatching() {
    TradingPartner customer = partner(PartnerType.CUSTOMER, "Acme%_Customer", true);
    TradingPartner both = partner(PartnerType.BOTH, null, true, "Acme%_Official");
    partner(PartnerType.SUPPLIER, "Acme%_Supplier", true);
    partner(PartnerType.CUSTOMER, "Acme%_Inactive", false);
    partner(PartnerType.CUSTOMER, "AcmeABWildcard", true);

    var matches =
        partnerRepository.findActiveCustomerIdsByNamePattern(
            tenantId, LikePattern.literalContains("Acme%_"), LikePattern.ESCAPE_CHARACTER);

    assertThat(matches).containsExactlyInAnyOrder(customer.getId(), both.getId());
  }

  private TradingPartner partner(PartnerType type, String customName, boolean active) {
    return partner(type, customName, active, "Registry " + UUID.randomUUID());
  }

  private TradingPartner partner(
      PartnerType type, String customName, boolean active, String officialName) {
    TradingPartnerRegistry registry = TradingPartnerRegistry.create(null, officialName, "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    registry = registryRepository.save(registry);

    TradingPartner partner = TradingPartner.create(registry, type, customName);
    partner.setTenantId(tenantId);
    partner.setIsActive(active);
    return partnerRepository.saveAndFlush(partner);
  }
}

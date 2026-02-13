package com.fabricmanagement.common.platform.tradingpartner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.tradingpartner.app.TradingPartnerRegistryService;
import com.fabricmanagement.common.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.common.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.common.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.common.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.common.platform.organization.domain.Organization;
import com.fabricmanagement.common.platform.organization.domain.OrganizationType;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.common.platform.tenant.domain.Tenant;
import com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for TradingPartner deduplication (Registry golden record).
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Registry deduplication: Same tax_id + country = same Registry
 *   <li>Multi-tenant isolation: Each tenant gets their own TradingPartner
 *   <li>Partner type upgrade: Adding same partner with different type → BOTH
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("TradingPartner deduplication")
class TradingPartnerDeduplicationIntegrationTest {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private TradingPartnerService tradingPartnerService;
  @Autowired private TradingPartnerRegistryService registryService;
  @Autowired private TradingPartnerRepository partnerRepository;
  @Autowired private TradingPartnerRegistryRepository registryRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;

  private UUID tenantA;
  private UUID tenantB;

  @BeforeEach
  void setUp() {
    // Create two tenants for multi-tenant tests
    tenantA = createTestTenant("Tenant A");
    tenantB = createTestTenant("Tenant B");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private UUID createTestTenant(String name) {
    long timestamp = System.currentTimeMillis();
    String uid = "TEST-" + timestamp % 100000;

    Tenant tenant = Tenant.create(name + " " + timestamp, uid);
    tenant.activate("test");
    tenant = tenantRepository.save(tenant);

    // Create root organization for tenant
    TenantContext.setCurrentTenantId(tenant.getId());
    Organization org =
        Organization.create(name + " Org", "TAX" + timestamp % 100000, OrganizationType.SPINNER);
    org = organizationRepository.save(org);
    TenantContext.clear();

    return tenant.getId();
  }

  @Nested
  @DisplayName("Registry Deduplication")
  class RegistryDeduplication {

    @Test
    @DisplayName("Two tenants adding partner with same tax_id share the same Registry")
    @Transactional
    void sameTaxId_sharesRegistry() {
      // Given: A shared partner tax ID
      String sharedTaxId = "SHARED" + System.currentTimeMillis() % 100000;
      String sharedName = "Shared Supplier Inc";

      // When: Tenant A creates a partner
      TenantContext.setCurrentTenantId(tenantA);
      TradingPartnerDto partnerA =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(sharedTaxId)
                  .companyName(sharedName)
                  .partnerType(PartnerType.SUPPLIER)
                  .country("TUR")
                  .build());

      // And: Tenant B creates a partner with same tax_id
      TenantContext.setCurrentTenantId(tenantB);
      TradingPartnerDto partnerB =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(sharedTaxId)
                  .companyName(sharedName)
                  .partnerType(PartnerType.CUSTOMER)
                  .country("TUR")
                  .build());

      // Then: Both partners should reference the same Registry
      assertThat(partnerA.getRegistryId()).isEqualTo(partnerB.getRegistryId());

      // And: Only one Registry should exist for this tax_id
      long registryCount =
          registryRepository.findAll().stream()
              .filter(r -> sharedTaxId.equals(r.getTaxId()))
              .count();
      assertThat(registryCount).isEqualTo(1);

      // And: Each tenant should have their own TradingPartner
      assertThat(partnerA.getId()).isNotEqualTo(partnerB.getId());
    }

    @Test
    @DisplayName("Different tax_ids create separate Registries")
    @Transactional
    void differentTaxId_separateRegistries() {
      // Given: Two different tax IDs
      String taxIdA = "TAXA" + System.currentTimeMillis() % 100000;
      String taxIdB = "TAXB" + System.currentTimeMillis() % 100000;

      // When: Same tenant creates partners with different tax_ids
      TenantContext.setCurrentTenantId(tenantA);
      TradingPartnerDto partnerA =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxIdA)
                  .companyName("Company A")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      TradingPartnerDto partnerB =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxIdB)
                  .companyName("Company B")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      // Then: They should have different Registries
      assertThat(partnerA.getRegistryId()).isNotEqualTo(partnerB.getRegistryId());
    }

    @Test
    @DisplayName("Same tax_id but different country creates separate Registries")
    @Transactional
    void sameTaxIdDifferentCountry_separateRegistries() {
      // Given: Same tax_id but different countries
      String taxId = "INTL" + System.currentTimeMillis() % 100000;

      // When: Create partners in different countries
      TenantContext.setCurrentTenantId(tenantA);
      TradingPartnerDto partnerTur =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Turkish Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .country("TUR")
                  .build());

      TradingPartnerDto partnerUsa =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("US Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .country("USA")
                  .build());

      // Then: They should have different Registries (same tax_id is allowed in different countries)
      assertThat(partnerTur.getRegistryId()).isNotEqualTo(partnerUsa.getRegistryId());
    }
  }

  @Nested
  @DisplayName("Partner Type Upgrade")
  class PartnerTypeUpgrade {

    @Test
    @DisplayName("Adding same partner as different type upgrades to BOTH")
    @Transactional
    void addingSamePartnerDifferentType_upgradesToBoth() {
      // Given: A partner added as SUPPLIER
      String taxId = "UPGRADE" + System.currentTimeMillis() % 100000;
      TenantContext.setCurrentTenantId(tenantA);

      TradingPartnerDto asSupplier =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Dual Role Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      assertThat(asSupplier.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);

      // When: Same tenant adds same partner as CUSTOMER
      TradingPartnerDto asCustomer =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Dual Role Company")
                  .partnerType(PartnerType.CUSTOMER)
                  .build());

      // Then: Partner type should be upgraded to BOTH
      assertThat(asCustomer.getPartnerType()).isEqualTo(PartnerType.BOTH);
      assertThat(asCustomer.getId()).isEqualTo(asSupplier.getId()); // Same partner, upgraded
    }

    @Test
    @DisplayName("Adding same partner with same type is idempotent")
    @Transactional
    void addingSamePartnerSameType_isIdempotent() {
      // Given: A partner added as SUPPLIER
      String taxId = "IDEMP" + System.currentTimeMillis() % 100000;
      TenantContext.setCurrentTenantId(tenantA);

      TradingPartnerDto first =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Idempotent Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      // When: Same tenant adds same partner as SUPPLIER again
      TradingPartnerDto second =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Idempotent Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      // Then: Should return same partner without changes
      assertThat(second.getId()).isEqualTo(first.getId());
      assertThat(second.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
    }

    @Test
    @DisplayName("BOTH type partner stays BOTH when adding new type")
    @Transactional
    void bothType_staysBoth() {
      // Given: A partner already at BOTH
      String taxId = "BOTH" + System.currentTimeMillis() % 100000;
      TenantContext.setCurrentTenantId(tenantA);

      // Create as SUPPLIER, then upgrade to BOTH
      tradingPartnerService.createPartner(
          CreateTradingPartnerRequest.builder()
              .taxId(taxId)
              .companyName("Both Company")
              .partnerType(PartnerType.SUPPLIER)
              .build());

      TradingPartnerDto asBoth =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Both Company")
                  .partnerType(PartnerType.CUSTOMER)
                  .build());

      assertThat(asBoth.getPartnerType()).isEqualTo(PartnerType.BOTH);

      // When: Add as SUPPLIER again
      TradingPartnerDto afterSupplier =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Both Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      // Then: Should stay BOTH
      assertThat(afterSupplier.getPartnerType()).isEqualTo(PartnerType.BOTH);
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  class TenantIsolation {

    @Test
    @DisplayName("Tenant A cannot see Tenant B's partners")
    @Transactional
    void tenantIsolation_cannotSeeCrossTenantPartners() {
      // Given: Tenant B creates a partner
      String taxId = "ISOLATED" + System.currentTimeMillis() % 100000;
      TenantContext.setCurrentTenantId(tenantB);

      TradingPartnerDto partnerB =
          tradingPartnerService.createPartner(
              CreateTradingPartnerRequest.builder()
                  .taxId(taxId)
                  .companyName("Isolated Company")
                  .partnerType(PartnerType.SUPPLIER)
                  .build());

      // When: Tenant A tries to find this partner
      TenantContext.setCurrentTenantId(tenantA);
      var fromTenantA = tradingPartnerService.findById(tenantA, partnerB.getId());

      // Then: Tenant A should not see it
      assertThat(fromTenantA).isEmpty();
    }

    @Test
    @DisplayName("Each tenant has separate partner counts even with shared Registry")
    @Transactional
    void sharedRegistry_separatePartnerCounts() {
      // Given: Both tenants add same partner (shared registry)
      String taxId = "COUNT" + System.currentTimeMillis() % 100000;

      TenantContext.setCurrentTenantId(tenantA);
      tradingPartnerService.createPartner(
          CreateTradingPartnerRequest.builder()
              .taxId(taxId)
              .companyName("Count Company")
              .partnerType(PartnerType.SUPPLIER)
              .build());

      TenantContext.setCurrentTenantId(tenantB);
      tradingPartnerService.createPartner(
          CreateTradingPartnerRequest.builder()
              .taxId(taxId)
              .companyName("Count Company")
              .partnerType(PartnerType.CUSTOMER)
              .build());

      // When: Count partners for each tenant
      TenantContext.setCurrentTenantId(tenantA);
      long countA = partnerRepository.findByTenantIdAndIsActiveTrue(tenantA).size();

      TenantContext.setCurrentTenantId(tenantB);
      long countB = partnerRepository.findByTenantIdAndIsActiveTrue(tenantB).size();

      // Then: Each tenant should see only their own partner
      assertThat(countA).isGreaterThanOrEqualTo(1);
      assertThat(countB).isGreaterThanOrEqualTo(1);
      // But only one shared registry
      long registryCount =
          registryRepository.findAll().stream().filter(r -> taxId.equals(r.getTaxId())).count();
      assertThat(registryCount).isEqualTo(1);
    }
  }
}

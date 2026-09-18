package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.flowboard.routing.domain.port.out.SalesOrderWriteScopePort;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class PermissionEvaluatorFreshnessIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("permission_freshness_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Autowired private PermissionEvaluator evaluator;
  @Autowired private SalesOrderAccessPolicy salesOrderAccessPolicy;
  @Autowired private SalesOrderWriteScopePort salesOrderWriteScopePort;
  @Autowired private PermissionTemplateRepository templateRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TradingPartnerRegistryRepository registryRepository;
  @Autowired private TradingPartnerRepository partnerRepository;
  @Autowired private SalesOrderRepository orderRepository;
  @Autowired private CacheManager cacheManager;
  @Autowired private JdbcTemplate jdbc;

  private UUID tenantId;
  private Role workerRole;
  private Organization organization;
  private String suffix;

  @BeforeEach
  void setUp() {
    clearPermissionCache();
    suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenant = tenantRepository.save(Tenant.create("Fresh " + suffix, "FR-" + suffix));
    tenantId = tenant.getId();
    TenantContext.setCurrentTenantId(tenantId);
    organization =
        organizationRepository.save(
            Organization.create("Fresh org " + suffix, "FT-" + suffix, OrganizationType.SPINNER));
    workerRole =
        roleRepository.save(Role.create("Fresh worker " + suffix, "WORKER-" + suffix, "Test"));
  }

  @AfterEach
  void clearContextAndCache() {
    TenantContext.clear();
    clearPermissionCache();
  }

  @Test
  void cachedEvaluationStaysStaleWhileFreshEvaluationSeesDatabaseChange() {
    User user = createUser("Cached", workerRole);
    PermissionTemplate template = grant(workerRole, DataScope.GLOBAL);

    PermissionResult cachedCold = evaluateCached(user);
    PermissionResult freshCold = evaluateFresh(user);
    assertThat(cachedCold).isEqualTo(freshCold);
    assertThat(cachedCold.scopeOf("sales", "write")).isEqualTo(DataScope.GLOBAL);

    narrowDirectlyInDatabase(template);

    assertThat(evaluateCached(user).scopeOf("sales", "write")).isEqualTo(DataScope.GLOBAL);
    assertThat(evaluateFresh(user).scopeOf("sales", "write")).isEqualTo(DataScope.OWN);
  }

  @Test
  void freshSalesPolicyAndPortRejectAfterDirectNarrowingWhileCachedPolicyDocumentsStaleness() {
    User target = createUser("Target", workerRole);
    User creator = createUser("Creator", workerRole);
    PermissionTemplate template = grant(workerRole, DataScope.GLOBAL);
    SalesOrder order = createOrder(creator.getId());

    assertThat(evaluateCached(target).scopeOf("sales", "write")).isEqualTo(DataScope.GLOBAL);
    narrowDirectlyInDatabase(template);

    assertThat(salesOrderAccessPolicy.canWrite(tenantId, target.getId(), order)).isTrue();
    assertThat(salesOrderWriteScopePort.isAllowed(tenantId, target.getId(), order.getId()))
        .isFalse();
    assertThat(
            salesOrderAccessPolicy.canWrite(
                tenantId, target.getId(), order, SalesOrderAccessPolicy.PermissionFreshness.FRESH))
        .isFalse();
  }

  private PermissionResult evaluateCached(User user) {
    return evaluator.evaluate(tenantId, workerRole.getRoleCode(), List.of(), user.getId());
  }

  private PermissionResult evaluateFresh(User user) {
    return evaluator.evaluateFresh(tenantId, workerRole.getRoleCode(), List.of(), user.getId());
  }

  private PermissionTemplate grant(Role role, DataScope scope) {
    TenantContext.setCurrentTenantId(tenantId);
    return templateRepository.saveAndFlush(
        PermissionTemplate.builder()
            .roleCode(role.getRoleCode())
            .resource("sales")
            .action("write")
            .dataScope(scope)
            .build());
  }

  private void narrowDirectlyInDatabase(PermissionTemplate template) {
    jdbc.update(
        "UPDATE common_user.permission_template SET data_scope = 'OWN' WHERE tenant_id = ? AND id = ?",
        tenantId,
        template.getId());
  }

  private User createUser(String firstName, Role role) {
    TenantContext.setCurrentTenantId(tenantId);
    User user = User.create(firstName, suffix, organization.getId());
    user.setRole(role);
    return userRepository.saveAndFlush(user);
  }

  private SalesOrder createOrder(UUID creatorId) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(creatorId);
    TradingPartnerRegistry registry = TradingPartnerRegistry.create(null, "Fresh partner", "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    registry = registryRepository.saveAndFlush(registry);
    TradingPartner partner =
        partnerRepository.saveAndFlush(
            TradingPartner.create(registry, PartnerType.CUSTOMER, "Fresh partner"));
    return orderRepository.saveAndFlush(
        SalesOrder.builder()
            .tradingPartnerId(partner.getId())
            .orderNumber("SO-FRESH-" + suffix)
            .status(OrderStatus.DRAFT)
            .orderDate(LocalDate.now())
            .totals(OrderTotals.zero("GBP"))
            .build());
  }

  private void clearPermissionCache() {
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }
}

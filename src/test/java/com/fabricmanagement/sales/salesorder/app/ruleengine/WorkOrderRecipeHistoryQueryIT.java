package com.fabricmanagement.sales.salesorder.app.ruleengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("WorkOrderRecipeHistoryQuery Integration Test")
class WorkOrderRecipeHistoryQueryIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
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

  @Autowired private NamedParameterJdbcTemplate jdbc;

  private final UUID tenantId = UUID.randomUUID();

  @Test
  @DisplayName(
      "Step 1: Query with NULL certification and origin binds correctly without Postgres type errors")
  void findDefaultRecipeForProduct_withNulls_shouldExecuteWithoutErrors() {
    WorkOrderRecipeHistoryQuery query = new WorkOrderRecipeHistoryQuery(jdbc);

    Optional<UUID> result =
        query.findDefaultRecipeForProduct(tenantId, UUID.randomUUID(), null, null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName(
      "Step 1: Query with populated certification and origin binds correctly without Postgres type errors")
  void findDefaultRecipeForProduct_withValues_shouldExecuteWithoutErrors() {
    WorkOrderRecipeHistoryQuery query = new WorkOrderRecipeHistoryQuery(jdbc);

    Optional<UUID> result =
        query.findDefaultRecipeForProduct(tenantId, UUID.randomUUID(), "GOTS", "TR");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Step 2: Customer history query executes without errors")
  void findMostRecentRecipeForCustomerAndProduct_shouldExecuteWithoutErrors() {
    WorkOrderRecipeHistoryQuery query = new WorkOrderRecipeHistoryQuery(jdbc);

    Optional<UUID> result =
        query.findMostRecentRecipeForCustomerAndProduct(
            tenantId, UUID.randomUUID(), UUID.randomUUID(), null, "TR");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Step 3: Most used recipe query executes without errors")
  void findMostUsedRecipeForProduct_shouldExecuteWithoutErrors() {
    WorkOrderRecipeHistoryQuery query = new WorkOrderRecipeHistoryQuery(jdbc);

    Optional<UUID> result =
        query.findMostUsedRecipeForProduct(tenantId, UUID.randomUUID(), "OEKO-TEX", null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName(
      "Step 1: Mixed-case BCI/IT DB value fails before backfill, succeeds after idempotent backfill")
  void findDefaultRecipeForProduct_withMixedCaseBciIt_shouldMatchOnlyAfterBackfill() {
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID blankRecipeId = UUID.randomUUID();
    UUID blankProductId = UUID.randomUUID();

    insertRecipeWithComponent(recipeId, productId, "bci", " it ");
    insertRecipeWithComponent(blankRecipeId, blankProductId, "   ", "");

    WorkOrderRecipeHistoryQuery query = new WorkOrderRecipeHistoryQuery(jdbc);

    Optional<UUID> beforeBackfill =
        query.findDefaultRecipeForProduct(tenantId, productId, "BCI", "IT");
    assertThat(beforeBackfill).isEmpty();

    assertThat(applyCertificationBackfill()).isEqualTo(2);
    assertThat(applyOriginBackfill()).isEqualTo(2);
    assertThat(applyCertificationBackfill()).isZero();
    assertThat(applyOriginBackfill()).isZero();

    Optional<UUID> afterBackfill =
        query.findDefaultRecipeForProduct(tenantId, productId, "BCI", "IT");
    assertThat(afterBackfill).contains(recipeId);

    Map<String, Object> blankValues =
        jdbc.getJdbcOperations()
            .queryForMap(
                "SELECT certification, origin FROM production.prod_recipe_component WHERE recipe_id = ?",
                blankRecipeId);
    assertThat(blankValues.get("certification")).isNull();
    assertThat(blankValues.get("origin")).isNull();
  }

  @Test
  @DisplayName("RuleEngine: tr_TR mixed-case BCI/IT request matches normalized recipe component")
  void processConfirmedOrder_withTurkishLocaleMixedCaseBciIt_shouldMatchNormalizedComponent() {
    UUID recipeId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID lineId = UUID.randomUUID();
    UUID partnerId = UUID.randomUUID();
    Locale previousLocale = Locale.getDefault();

    insertRecipeWithComponent(recipeId, productId, "BCI", "IT");

    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TenantContext.setCurrentTenantId(tenantId);

      SalesOrderLineRepository lineRepository = mock(SalesOrderLineRepository.class);
      ProductionOrderPort productionOrderPort = mock(ProductionOrderPort.class);
      SalesOrderRuleEngine ruleEngine =
          new SalesOrderRuleEngine(
              lineRepository,
              productionOrderPort,
              new WorkOrderRecipeHistoryQuery(jdbc),
              mock(DomainEventPublisher.class));

      SalesOrder order =
          SalesOrder.builder().tradingPartnerId(partnerId).orderNumber("SO-TEST").build();
      ReflectionTestUtils.setField(order, "id", orderId);

      SalesOrderLine line =
          SalesOrderLine.builder()
              .productId(productId)
              .moduleType(ModuleType.FIBER)
              .moduleSpecs(Map.of("certificationReq", "bci", "originReq", " it "))
              .requestedQty(BigDecimal.ONE)
              .unit("KG")
              .build();
      ReflectionTestUtils.setField(line, "id", lineId);

      when(lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
              orderId, SalesOrderLineStatus.PENDING))
          .thenReturn(List.of(line));

      ruleEngine.processConfirmedOrder(order);

      assertThat(line.getRecipeId()).isEqualTo(recipeId);
      verify(lineRepository).save(line);

      ArgumentCaptor<DraftProductionOrderCommand> commandCaptor =
          ArgumentCaptor.forClass(DraftProductionOrderCommand.class);
      verify(productionOrderPort).requestDraftProductionOrder(commandCaptor.capture());
      assertThat(commandCaptor.getValue().recipeId()).isEqualTo(recipeId);
      assertThat(commandCaptor.getValue().certificationReq()).isEqualTo("BCI");
      assertThat(commandCaptor.getValue().originReq()).isEqualTo("IT");
    } finally {
      TenantContext.clear();
      Locale.setDefault(previousLocale);
    }
  }

  private void insertRecipeWithComponent(
      UUID recipeId, UUID productId, String certification, String origin) {
    jdbc.getJdbcOperations()
        .update(
            "INSERT INTO production.prod_recipe (id, tenant_id, uid, created_at, updated_at, name, iso_code, components, status, recipe_version, is_active) "
                + "VALUES (?, ?, ?, now(), now(), 'Test Recipe', 'TEST', '[]', 'ACTIVE', 1, true)",
            recipeId,
            tenantId,
            UUID.randomUUID().toString());

    jdbc.getJdbcOperations()
        .update(
            "INSERT INTO production.prod_recipe_component (id, tenant_id, uid, created_at, updated_at, recipe_id, fiber_id, fiber_name, fiber_iso_code, percentage, certification, origin, display_order, is_active) "
                + "VALUES (?, ?, ?, now(), now(), ?, ?, 'Cotton', 'CO', 100.0, ?, ?, 1, true)",
            UUID.randomUUID(),
            tenantId,
            UUID.randomUUID().toString(),
            recipeId,
            productId,
            certification,
            origin);
  }

  private int applyCertificationBackfill() {
    return jdbc.getJdbcOperations()
        .update(
            "UPDATE production.prod_recipe_component "
                + "SET certification = NULLIF(UPPER(TRIM(certification)), '') "
                + "WHERE certification IS DISTINCT FROM NULLIF(UPPER(TRIM(certification)), '')");
  }

  private int applyOriginBackfill() {
    return jdbc.getJdbcOperations()
        .update(
            "UPDATE production.prod_recipe_component "
                + "SET origin = NULLIF(UPPER(TRIM(origin)), '') "
                + "WHERE origin IS DISTINCT FROM NULLIF(UPPER(TRIM(origin)), '')");
  }
}

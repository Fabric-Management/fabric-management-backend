package com.fabricmanagement.procurement.purchaseorder.infra.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderConstraintViolationMatcher;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PurchaseOrderRepositoryConstraintIT {

  private static final UUID TENANT_ONE = UUID.fromString("88888888-8888-4888-8888-888888888881");
  private static final UUID TENANT_TWO = UUID.fromString("88888888-8888-4888-8888-888888888882");

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(
            "DO $$ BEGIN "
                + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN "
                + "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'; "
                + "END IF; END $$");
      }
    } catch (Exception exception) {
      throw new RuntimeException("Failed to create fabric_app role", exception);
    }

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "app_test");
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private PurchaseOrderRepository purchaseOrderRepository;
  @Autowired private TenantSessionBinder tenantSessionBinder;
  @Autowired private TransactionTemplate transactionTemplate;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void activeSupplierQuoteConstraint_isTenantScopedAndIgnoresInactiveRows() throws Exception {
    seedTenantAsOwner(TENANT_ONE, "PO-CONSTRAINT-ONE", "po-constraint-one");
    seedTenantAsOwner(TENANT_TWO, "PO-CONSTRAINT-TWO", "po-constraint-two");
    UUID supplierQuoteId = UUID.randomUUID();

    savePurchaseOrder(TENANT_ONE, supplierQuoteId, true);

    assertThatThrownBy(() -> savePurchaseOrder(TENANT_ONE, supplierQuoteId, true))
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(
                        PurchaseOrderConstraintViolationMatcher.isActiveSupplierQuoteViolation(
                            exception))
                    .isTrue());

    assertThatCode(() -> savePurchaseOrder(TENANT_TWO, supplierQuoteId, true))
        .doesNotThrowAnyException();
    assertThatCode(() -> savePurchaseOrder(TENANT_ONE, supplierQuoteId, false))
        .doesNotThrowAnyException();
  }

  private void savePurchaseOrder(UUID tenantId, UUID supplierQuoteId, boolean active) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentTenantUid("PO-CONSTRAINT");
    transactionTemplate.executeWithoutResult(
        status -> {
          tenantSessionBinder.bindToCurrentSession(tenantId);
          PurchaseOrder purchaseOrder =
              PurchaseOrder.builder()
                  .poNumber("PO-CONSTRAINT-" + UUID.randomUUID())
                  .workOrderId(UUID.randomUUID())
                  .tradingPartnerId(UUID.randomUUID())
                  .supplierQuoteId(supplierQuoteId)
                  .status(PurchaseOrderStatus.DRAFT)
                  .totalAmount(Money.zero("USD"))
                  .build();
          purchaseOrder.setIsActive(active);
          purchaseOrderRepository.saveAndFlush(purchaseOrder);
        });
  }

  private void seedTenantAsOwner(UUID tenantId, String uid, String slug) throws Exception {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      try (var ps =
          conn.prepareStatement(
              "INSERT INTO common_tenant.common_tenant "
                  + "(id, uid, slug, name, status, settings, is_active, created_at, updated_at, version) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE', '{}'::jsonb, true, now(), now(), 0) "
                  + "ON CONFLICT (id) DO NOTHING")) {
        ps.setObject(1, tenantId);
        ps.setString(2, uid);
        ps.setString(3, slug);
        ps.setString(4, uid);
        ps.executeUpdate();
      }
    }
  }
}

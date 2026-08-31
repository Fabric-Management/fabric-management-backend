package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

abstract class FiberSourceIntegrationSupport extends AbstractIntegrationTest {

  static final String CATEGORY = "SYNTHETIC_POLYMER";

  @Autowired protected FiberRequestService fiberRequestService;
  @Autowired protected FiberService fiberService;
  @Autowired protected SystemTransactionExecutor systemTransactions;

  @MockBean protected InAppNotificationService notificationService;

  @AfterEach
  void clearFiberSourceTenantContext() {
    TenantContext.clear();
  }

  protected UUID insertTenant(String label) {
    UUID tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "FSRC-" + suffix.toUpperCase(),
              "fiber-source-" + label.toLowerCase() + "-" + suffix,
              "Fiber Source " + label);
          return null;
        });
    return tenantId;
  }

  protected UUID insertCategory(UUID tenantId, String categoryCode) {
    UUID id = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_fiber_category "
                  + "(id, tenant_id, uid, category_code, category_name, is_active) "
                  + "VALUES (?, ?, ?, ?, ?, TRUE)",
              id,
              tenantId,
              uid("FCAT"),
              categoryCode,
              categoryCode + " Test Category");
          return null;
        });
    return id;
  }

  protected UUID insertIso(UUID tenantId, String isoCode, String fiberType) {
    UUID id = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_fiber_iso_code "
                  + "(id, tenant_id, uid, iso_code, fiber_name, fiber_type, is_official_iso, is_active) "
                  + "VALUES (?, ?, ?, ?, ?, ?, FALSE, TRUE)",
              id,
              tenantId,
              uid("FISO"),
              isoCode,
              isoCode + " Test Fiber",
              fiberType);
          return null;
        });
    return id;
  }

  protected UUID insertPureFiber(
      UUID tenantId, UUID categoryId, UUID isoId, String fiberName, MaterialSource source) {
    return insertFiber(tenantId, categoryId, isoId, fiberName, source, "{}");
  }

  protected UUID insertBlend(
      UUID tenantId, UUID categoryId, UUID primaryIsoId, String fiberName, String composition) {
    return insertFiber(tenantId, categoryId, primaryIsoId, fiberName, null, composition);
  }

  private UUID insertFiber(
      UUID tenantId,
      UUID categoryId,
      UUID isoId,
      String fiberName,
      MaterialSource source,
      String composition) {
    UUID productId = UUID.randomUUID();
    UUID fiberId = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'FIBER', 'KG', TRUE)",
              productId,
              tenantId,
              uid("PROD"));
          jdbc.update(
              "INSERT INTO production.prod_fiber "
                  + "(id, tenant_id, uid, product_id, fiber_category_id, fiber_iso_code_id, "
                  + "fiber_name, composition, status, material_source, is_active) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'ACTIVE', ?, TRUE)",
              fiberId,
              tenantId,
              uid("FIBR"),
              productId,
              categoryId,
              isoId,
              fiberName,
              composition,
              source != null ? source.name() : null);
          return null;
        });
    return fiberId;
  }

  protected UUID insertPendingRequest(
      UUID tenantId,
      UUID requestedBy,
      String isoCode,
      String fiberName,
      String fiberType,
      MaterialSource source) {
    UUID requestId = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.production_fiber_request "
                  + "(id, tenant_id, uid, requested_by, iso_code, fiber_name, fiber_type, "
                  + "material_source, status, is_active) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', TRUE)",
              requestId,
              tenantId,
              uid("FREQ"),
              requestedBy,
              isoCode,
              fiberName,
              fiberType,
              source != null ? source.name() : null);
          return null;
        });
    return requestId;
  }

  protected CreateFiberRequestRequest request(
      String isoCode, String fiberName, String fiberType, MaterialSource source) {
    return CreateFiberRequestRequest.builder()
        .isoCode(isoCode)
        .fiberName(fiberName)
        .fiberType(fiberType)
        .materialSource(source)
        .build();
  }

  protected void useTenant(UUID tenantId, UUID actorId) {
    TenantContext.restore(
        new TenantContext.TenantSnapshot(
            tenantId, "FSRC-" + tenantId.toString().substring(0, 8).toUpperCase(), actorId, null));
  }

  protected <T> T queryOne(String sql, Class<T> type, Object... args) {
    return systemTransactions.executeInTransaction(jdbc -> jdbc.queryForObject(sql, type, args));
  }

  protected int update(String sql, Object... args) {
    return systemTransactions.executeInTransaction(jdbc -> jdbc.update(sql, args));
  }

  protected String uid(String module) {
    return "FSRC-"
        + module
        + "-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }
}

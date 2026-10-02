package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Real-DB support for fibre catalogue tests (FIBER-CATALOG-1). Reference data comes from the shared
 * catalogue published by the migration chain; tests never create tenant ISO/category rows (the
 * owner CHECK forbids them). New codes are published as a platform release would publish them: an
 * ISO row under the catalogue owner plus {@code publish_fiber_catalog_entry}.
 */
abstract class FiberSourceIntegrationSupport extends AbstractIntegrationTest {

  static final String CATEGORY = "SYNTHETIC_POLYMER";
  static final UUID MIXED_BLEND_CATEGORY_ID =
      UUID.fromString("0f1bca70-0000-4000-8000-000000000008");

  @Autowired protected FiberRequestService fiberRequestService;
  @Autowired protected FiberService fiberService;
  @Autowired protected SystemTransactionExecutor systemTransactions;

  @MockitoBean protected InAppNotificationService notificationService;

  @AfterEach
  void clearFiberSourceTenantContext() {
    TenantContext.clear();
  }

  protected UUID insertTenant(String label) {
    return insertTenant(label, "REGULAR");
  }

  protected UUID insertTenant(String label, String type) {
    UUID tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, status) "
                  + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "FSRC-" + suffix.toUpperCase(Locale.ROOT),
              "fiber-source-" + label.toLowerCase(Locale.ROOT) + "-" + suffix,
              "Fiber Source " + label,
              type);
          return null;
        });
    return tenantId;
  }

  protected UUID sharedIsoId(String isoCode) {
    return queryOne(
        "SELECT id FROM production.prod_fiber_iso_code WHERE tenant_id = ? AND iso_code = ?",
        UUID.class,
        FiberCatalog.OWNER_ID,
        isoCode);
  }

  protected UUID sharedCategoryId(String categoryCode) {
    return queryOne(
        "SELECT id FROM production.prod_fiber_category WHERE tenant_id = ? AND category_code = ?",
        UUID.class,
        FiberCatalog.OWNER_ID,
        categoryCode);
  }

  protected UUID canonicalFiberId(String isoCode) {
    return queryOne(
        "SELECT f.id FROM production.prod_fiber f "
            + "JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id "
            + "WHERE f.tenant_id = ? AND i.iso_code = ? AND f.material_source IS NULL",
        UUID.class,
        FiberCatalog.OWNER_ID,
        isoCode);
  }

  protected UUID canonicalProductId(String isoCode) {
    return queryOne(
        "SELECT product_id FROM production.prod_fiber WHERE id = ?",
        UUID.class,
        canonicalFiberId(isoCode));
  }

  /** Publishes a new shared ISO row only (an incomplete publication: no canonical fibre). */
  protected String publishIsoRowOnly(String fiberType) {
    String isoCode = randomCode();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_fiber_iso_code "
                  + "(id, tenant_id, uid, iso_code, fiber_name, fiber_type, is_official_iso, "
                  + "is_active) VALUES (?, ?, ?, ?, ?, ?, FALSE, TRUE)",
              UUID.randomUUID(),
              FiberCatalog.OWNER_ID,
              uid("FISO"),
              isoCode,
              isoCode + " Test Fiber",
              fiberType);
          return null;
        });
    return isoCode;
  }

  /** Publishes a new shared code the way a platform catalogue release does. */
  protected String publishCatalogueCode(String fiberType) {
    String isoCode = publishIsoRowOnly(fiberType);
    completePublication(isoCode);
    return isoCode;
  }

  protected void completePublication(String isoCode) {
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.queryForList(
              "SELECT production.publish_fiber_catalog_entry(?, ?, ?, ?, ?, ?)",
              isoCode,
              UUID.randomUUID(),
              uid("SYS-MAT"),
              UUID.randomUUID(),
              uid("SYS-FIB"),
              isoCode + " Test Fiber (100%)");
          return null;
        });
  }

  /** A tenant-private pure variant of a shared ISO code, inserted directly. */
  protected UUID insertPrivateVariant(
      UUID tenantId, String isoCode, String categoryCode, String name, MaterialSource source) {
    return insertFiber(
        tenantId, sharedCategoryId(categoryCode), sharedIsoId(isoCode), name, source, "{}");
  }

  /** A tenant blend inserted directly (null ISO, shared MIXED_BLEND). */
  protected UUID insertBlend(UUID tenantId, String fiberName, String compositionJson) {
    return insertFiber(tenantId, MIXED_BLEND_CATEGORY_ID, null, fiberName, null, compositionJson);
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
            tenantId,
            "FSRC-" + tenantId.toString().substring(0, 8).toUpperCase(Locale.ROOT),
            actorId,
            null));
  }

  protected <T> T queryOne(String sql, Class<T> type, Object... args) {
    return systemTransactions.executeInTransaction(jdbc -> jdbc.queryForObject(sql, type, args));
  }

  protected int update(String sql, Object... args) {
    return systemTransactions.executeInTransaction(jdbc -> jdbc.update(sql, args));
  }

  protected long count(String sql, Object... args) {
    return queryOne(sql, Long.class, args);
  }

  protected String uid(String module) {
    return "FSRC-"
        + module
        + "-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
  }

  protected static String randomCode() {
    return "N" + UUID.randomUUID().toString().substring(0, 7).toUpperCase(Locale.ROOT);
  }
}

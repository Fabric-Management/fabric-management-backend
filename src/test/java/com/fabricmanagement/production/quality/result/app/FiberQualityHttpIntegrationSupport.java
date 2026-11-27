package com.fabricmanagement.production.quality.result.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.app.EvaluationSubscriptionService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.core.stockunit.app.StockUnitService;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fibre-quality HTTP fixture with a demo-mode trial tenant, impersonation JWTs and exact quality
 * grants.
 */
abstract class FiberQualityHttpIntegrationSupport extends AbstractFabricAppIntegrationTest {

  protected static final String QUALITY = "/api/v1/production/quality";
  protected static final String TESTS = QUALITY + "/fiber-tests";

  @Autowired
  @Qualifier("systemJdbcTemplate")
  protected JdbcTemplate systemJdbc;

  @Autowired protected StockUnitService stockUnits;
  @Autowired protected ObjectMapper json;
  @Autowired protected MockMvc mvc;

  @Autowired private BatchService batches;
  @Autowired private OrganizationRepository organisations;
  @Autowired private RoleRepository roles;
  @Autowired private UserRepository users;
  @Autowired private PermissionTemplateRepository permissions;
  @Autowired private EvaluationSubscriptionService evaluations;
  @Autowired private JwtService jwt;

  protected UUID tenantId;
  private UUID organisationId;
  private UUID cottonProductId;

  @BeforeEach
  void createQualityTenant() {
    tenantId = insertTenant();
    useTenant(tenantId);
    evaluations.initialisePlayground(tenantId);
    organisationId =
        organisations
            .saveAndFlush(
                Organization.create(
                    "Alder Beck Spinning Ltd", "GB123456789", OrganizationType.SPINNER))
            .getId();
    cottonProductId =
        systemJdbc.queryForObject(
            "SELECT f.product_id FROM production.prod_fiber f"
                + " JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id"
                + " WHERE f.tenant_id = ? AND i.iso_code = 'CO' AND f.material_source IS NULL",
            UUID.class,
            FiberCatalog.OWNER_ID);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  protected UUID insertTenant() {
    UUID newTenantId = UUID.randomUUID();
    systemJdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, demo_mode, status)"
            + " VALUES (?, ?, ?, 'Alder Beck Spinning Ltd', 'REGULAR', true, 'TRIAL')",
        newTenantId,
        "ABS-" + newTenantId,
        "alder-beck-" + newTenantId);
    return newTenantId;
  }

  protected void useTenant(UUID activeTenantId) {
    TenantContext.setCurrentTenantId(activeTenantId);
    TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
  }

  protected UUID createLot(String batchCode, BigDecimal quantity) {
    return batches
        .create(
            CreateBatchRequest.builder()
                .productId(cottonProductId)
                .productType(ProductType.FIBER)
                .batchCode(batchCode)
                .quantity(quantity)
                .unit("KG")
                .sourceType(BatchSourceType.INITIAL_STOCK)
                .build())
        .getId();
  }

  protected UUID createBale(UUID batchId, String barcode, BigDecimal weight) {
    return stockUnits
        .create(
            batchId,
            ProductType.FIBER,
            barcode,
            null,
            PackageType.BALE,
            weight,
            null,
            "KG",
            null,
            null,
            null,
            StockUnitSourceType.PRODUCTION,
            batchId)
        .getId();
  }

  protected String token(String firstName, String lastName, String roleCode, String action) {
    Role role = roles.saveAndFlush(Role.create(roleCode, roleCode, "Fibre quality"));
    permissions.saveAndFlush(
        PermissionTemplate.builder()
            .roleCode(roleCode)
            .resource("quality")
            .action(action)
            .dataScope(DataScope.ORGANIZATION)
            .build());
    User user = User.create(firstName, lastName, organisationId);
    user.setRole(role);
    UUID userId = users.saveAndFlush(user).getId();
    return jwt.generateDemoImpersonationAccessToken(
        users.findByIdWithPermissionData(tenantId, userId).orElseThrow(),
        null,
        firstName.toLowerCase(Locale.ROOT) + "@example.com");
  }
}

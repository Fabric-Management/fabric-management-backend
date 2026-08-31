package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleAuditRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

abstract class YarnArticleIntegrationSupport extends AbstractIntegrationTest {

  @Autowired protected YarnArticleService service;
  @Autowired protected YarnArticleAuditRepository auditRepository;
  @Autowired protected SystemTransactionExecutor systemTransactions;

  @AfterEach
  void clearYarnTenant() {
    TenantContext.clear();
  }

  protected Fixture insertFixture(String label) {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID yarnProductId = UUID.randomUUID();
    UUID fiberProductId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID isoId = UUID.randomUUID();
    UUID fiberId = UUID.randomUUID();
    UUID spinningSystemId = UUID.randomUUID();
    UUID testMethodId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8).toUpperCase();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "YAI-" + suffix,
              "yarn-article-" + label.toLowerCase() + "-" + suffix.toLowerCase(),
              "Yarn Article " + label);
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'YARN', 'KG', TRUE)",
              yarnProductId,
              tenantId,
              uid("YPROD"));
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'FIBER', 'KG', TRUE)",
              fiberProductId,
              tenantId,
              uid("FPROD"));
          jdbc.update(
              "INSERT INTO production.prod_fiber_category "
                  + "(id, tenant_id, uid, category_code, category_name, is_active) "
                  + "VALUES (?, ?, ?, ?, 'Natural fiber', TRUE)",
              categoryId,
              tenantId,
              uid("FCAT"),
              "NATURAL_" + suffix);
          jdbc.update(
              "INSERT INTO production.prod_fiber_iso_code "
                  + "(id, tenant_id, uid, iso_code, fiber_name, fiber_type, is_official_iso, is_active) "
                  + "VALUES (?, ?, ?, ?, 'Cotton', 'NATURAL', TRUE, TRUE)",
              isoId,
              tenantId,
              uid("FISO"),
              "C" + suffix.substring(0, 6));
          jdbc.update(
              "INSERT INTO production.prod_fiber "
                  + "(id, tenant_id, uid, product_id, fiber_category_id, fiber_iso_code_id, "
                  + "fiber_name, composition, status, material_source, is_active) "
                  + "VALUES (?, ?, ?, ?, ?, ?, 'Tenant Cotton', '{}'::jsonb, 'ACTIVE', 'VIRGIN', TRUE)",
              fiberId,
              tenantId,
              uid("FIB"),
              fiberProductId,
              categoryId,
              isoId);
          jdbc.update(
              "INSERT INTO production.prod_yarn_spinning_system "
                  + "(id, tenant_id, uid, code, name, technology_family, system_defined, is_active) "
                  + "VALUES (?, ?, ?, 'RING', 'Ring', 'RING', TRUE, TRUE)",
              spinningSystemId,
              tenantId,
              uid("YSPN"));
          jdbc.update(
              "INSERT INTO production.prod_yarn_test_method "
                  + "(id, tenant_id, uid, code, name, system_defined, is_active) "
                  + "VALUES (?, ?, ?, 'ISO_2061', 'ISO 2061', TRUE, TRUE)",
              testMethodId,
              tenantId,
              uid("YTST"));
          return null;
        });
    return new Fixture(tenantId, actorId, yarnProductId, fiberId, spinningSystemId, testMethodId);
  }

  protected void use(Fixture fixture) {
    TenantContext.restore(
        new TenantContext.TenantSnapshot(
            fixture.tenantId(),
            "YAI-" + fixture.tenantId().toString().substring(0, 8).toUpperCase(),
            fixture.actorId(),
            null));
  }

  protected YarnArticleSpecCommand command(
      Fixture fixture, String count, String sourceDesignation) {
    return new YarnArticleSpecCommand(
        CountSystem.TEX,
        new BigDecimal(count),
        CountBasis.COMPONENT,
        YarnStructureType.SINGLE,
        1,
        null,
        null,
        sourceDesignation,
        YarnMaterialForm.STAPLE_SPUN,
        SpinningTechnologyFamily.RING,
        fixture.spinningSystemId(),
        null,
        Set.of(),
        List.of(
            new YarnArticleSpecCommand.CompositionCommand(
                fixture.fiberId(), new BigDecimal("100"))),
        List.of(),
        List.of(
            new YarnArticleSpecCommand.TwistStageCommand(
                TwistStageType.SINGLE,
                1,
                TwistDirection.Z,
                new BigDecimal("800"),
                null,
                fixture.testMethodId())));
  }

  protected YarnArticle create(Fixture fixture) {
    use(fixture);
    return service.createDraft(
        fixture.yarnProductId(), "Integration yarn", "created", command(fixture, "20", "20 tex"));
  }

  protected <T> T queryOne(String sql, Class<T> type, Object... args) {
    return systemTransactions.executeInTransaction(jdbc -> jdbc.queryForObject(sql, type, args));
  }

  private static String uid(String module) {
    return "YAI-"
        + module
        + "-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  protected record Fixture(
      UUID tenantId,
      UUID actorId,
      UUID yarnProductId,
      UUID fiberId,
      UUID spinningSystemId,
      UUID testMethodId) {}
}

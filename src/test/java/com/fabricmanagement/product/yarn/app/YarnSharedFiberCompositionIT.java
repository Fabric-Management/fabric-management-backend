package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FIBER-CATALOG-1 A21: yarn composition keeps working on the shared catalogue. A shared canonical
 * pure fibre is accepted (the tenant's private pure variant is covered by the support fixture),
 * while a tenant blend (null ISO) never enters the pure-only yarn composition path.
 */
class YarnSharedFiberCompositionIT extends YarnArticleIntegrationSupport {

  private static final UUID MIXED_BLEND_CATEGORY_ID =
      UUID.fromString("0f1bca70-0000-4000-8000-000000000008");

  private UUID sharedCanonical(String isoCode) {
    return queryOne(
        "SELECT f.id FROM production.prod_fiber f "
            + "JOIN production.prod_fiber_iso_code i ON i.id = f.fiber_iso_code_id "
            + "WHERE f.tenant_id = ? AND i.iso_code = ? AND f.material_source IS NULL",
        UUID.class,
        FiberCatalog.OWNER_ID,
        isoCode);
  }

  private static Fixture withFiber(Fixture fixture, UUID fiberId) {
    return new Fixture(
        fixture.tenantId(),
        fixture.actorId(),
        fixture.yarnProductId(),
        fiberId,
        fixture.spinningSystemId(),
        fixture.testMethodId());
  }

  private UUID insertBlend(Fixture fixture, UUID first, UUID second) {
    UUID productId = UUID.randomUUID();
    UUID blendId = UUID.randomUUID();
    String suffix = blendId.toString().replace("-", "").substring(0, 12).toUpperCase();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'FIBER', 'KG', TRUE)",
              productId,
              fixture.tenantId(),
              "YAI-BPROD-" + suffix);
          jdbc.update(
              "INSERT INTO production.prod_fiber "
                  + "(id, tenant_id, uid, product_id, fiber_category_id, fiber_iso_code_id, "
                  + "fiber_name, composition, status, material_source, is_active) "
                  + "VALUES (?, ?, ?, ?, ?, NULL, 'Tenant blend', ?::jsonb, 'ACTIVE', NULL, TRUE)",
              blendId,
              fixture.tenantId(),
              "YAI-BFIB-" + suffix,
              productId,
              MIXED_BLEND_CATEGORY_ID,
              "{\"" + first + "\":60,\"" + second + "\":40}");
          return null;
        });
    return blendId;
  }

  @Test
  void aSharedCanonicalPureFibreIsAcceptedAsAYarnComponent() {
    Fixture fixture = withFiber(insertFixture("shared-co"), sharedCanonical("CO"));
    use(fixture);

    YarnArticle article =
        service.createDraft(
            fixture.yarnProductId(),
            "Shared cotton yarn",
            "created",
            command(fixture, "20", "20 tex"));

    assertThat(article.getId()).isNotNull();
  }

  @Test
  void aTenantBlendNeverEntersThePureOnlyYarnComposition() {
    Fixture base = insertFixture("blend-reject");
    UUID blend = insertBlend(base, sharedCanonical("CO"), sharedCanonical("PES"));
    Fixture fixture = withFiber(base, blend);
    use(fixture);

    assertThatThrownBy(
            () ->
                service.createDraft(
                    fixture.yarnProductId(),
                    "Blend yarn",
                    "created",
                    command(fixture, "20", "20 tex")))
        .isInstanceOfSatisfying(
            YarnDomainException.class,
            failure -> assertThat(failure.getInvariantIds()).contains("I14"));
  }
}

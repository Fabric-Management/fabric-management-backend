package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import org.junit.jupiter.api.Test;

class YarnArticleTenantIsolationIT extends YarnArticleIntegrationSupport {

  @Test
  void tenantBHasNoReadUpdateOrActivationAccessToTenantAArticle() {
    Fixture tenantA = insertFixture("tenant-a");
    Fixture tenantB = insertFixture("tenant-b");
    YarnArticle owned = create(tenantA);

    use(tenantB);
    assertThat(service.findById(owned.getId())).isEmpty();
    assertThatThrownBy(() -> service.updateSpec(owned.getId(), command(tenantB, "30", "tenant B")))
        .isInstanceOf(YarnDomainException.class);
    assertThatThrownBy(() -> service.activate(owned.getId()))
        .isInstanceOf(YarnDomainException.class);

    YarnArticle independent = create(tenantB);
    assertThat(independent.getTenantId()).isEqualTo(tenantB.tenantId());
    assertThat(independent.getProductId()).isEqualTo(tenantB.yarnProductId());
  }

  @Test
  void canonicalKeyIsAdvisoryAndDoesNotBlockTwoEqualArticles() {
    Fixture fixture = insertFixture("duplicate-key");
    YarnArticle first = create(fixture);
    java.util.UUID secondProductId = java.util.UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'YARN', 'KG', TRUE)",
              secondProductId,
              fixture.tenantId(),
              "YAI-YPROD-" + java.util.UUID.randomUUID().toString().substring(0, 8));
          return null;
        });

    use(fixture);
    YarnArticle second =
        service.createDraft(
            secondProductId,
            "Same identity, deliberate article",
            null,
            command(fixture, "20", "different supplier wording"));

    assertThat(second.getCanonicalKey()).isEqualTo(first.getCanonicalKey());
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_yarn_article "
                    + "WHERE tenant_id = ? AND canonical_key = ?",
                Long.class,
                fixture.tenantId(),
                first.getCanonicalKey()))
        .isEqualTo(2L);
  }
}

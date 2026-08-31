package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class YarnArticleQueryIT extends YarnArticleIntegrationSupport {

  @Test
  void combinesStatusLiteralTextAndTexRangeFiltersInAStablePage() {
    Fixture fixture = insertFixture("query");
    use(fixture);
    YarnArticle active =
        service.createDraft(
            fixture.yarnProductId(),
            "Everyday ring yarn",
            null,
            command(fixture, "20", "supplier alpha"));
    service.activate(active.getId());

    UUID matchingProduct = insertYarnProduct(fixture, "MATCH");
    YarnArticle matching =
        service.createDraft(
            matchingProduct,
            "Rotor special % literal",
            null,
            command(fixture, "30", "supplier beta"));
    UUID outsideProduct = insertYarnProduct(fixture, "OUTSIDE");
    service.createDraft(
        outsideProduct, "Rotor coarse", null, command(fixture, "40", "supplier gamma"));

    var result =
        service.list(
            YarnArticleStatus.DRAFT,
            "% literal",
            new BigDecimal("29.00"),
            new BigDecimal("31.00"),
            PageRequest.of(0, 10, Sort.by("createdAt").descending()));

    assertThat(result.getContent())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.id()).isEqualTo(matching.getId());
              assertThat(row.resultantLinearDensityTex()).isEqualByComparingTo("30.00");
            });
    assertThat(
            service
                .list(YarnArticleStatus.ACTIVE, null, null, null, PageRequest.of(0, 10))
                .getContent())
        .extracting(row -> row.id())
        .containsExactly(active.getId());
  }

  private UUID insertYarnProduct(Fixture fixture, String label) {
    UUID productId = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'YARN', 'KG', TRUE)",
              productId,
              fixture.tenantId(),
              "YAI-" + label + "-" + UUID.randomUUID().toString().substring(0, 8));
          return null;
        });
    return productId;
  }
}

package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.yarn.app.adapter.YarnAIToolProvider;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class YarnArticleQueryIT extends YarnArticleIntegrationSupport {

  @Autowired private YarnAIToolProvider aiToolProvider;

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

  @Test
  void aiSearchReturnsTheSameTenantArticleForTurkishAndEnglishQueries() {
    Fixture fixture = insertFixture("ai-search");
    use(fixture);
    YarnArticle article =
        service.createDraft(
            fixture.yarnProductId(),
            "Combed cotton yarn 30/2",
            null,
            command(fixture, "30", "Ne 30/2"));
    service.activate(article.getId());

    assertThat(service.findViewByUid(article.getUid()).orElseThrow().id())
        .isEqualTo(article.getId());
    assertThat(service.findViewsByName(article.getName()))
        .extracting(view -> view.id())
        .containsExactly(article.getId());
    assertThat(service.getViewByProductId(article.getProductId()).id()).isEqualTo(article.getId());

    String turkish =
        aiToolProvider.execute(
            fixture.tenantId(),
            "search_yarns",
            Map.of("query", "penye pamuk iplik 30/2", "status", "ACTIVE"));
    String english =
        aiToolProvider.execute(
            fixture.tenantId(),
            "search_yarns",
            Map.of("query", "combed cotton yarn 30/2", "status", "ACTIVE"));
    String wrongStatus =
        aiToolProvider.execute(
            fixture.tenantId(),
            "search_yarns",
            Map.of("query", "combed cotton yarn 30/2", "status", "DRAFT"));

    assertThat(turkish).isEqualTo(english).contains(article.getUid());
    assertThat(wrongStatus).contains("No yarn article found").doesNotContain(article.getUid());
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

package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.yarn.app.adapter.YarnAIToolProvider;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class YarnArticleTenantIsolationIT extends YarnArticleIntegrationSupport {

  @Autowired private YarnAIToolProvider aiToolProvider;

  @Test
  void tenantBHasNoReadUpdateOrActivationAccessToTenantAArticle() {
    Fixture tenantA = insertFixture("tenant-a");
    Fixture tenantB = insertFixture("tenant-b");
    YarnArticle owned = create(tenantA);

    use(tenantB);
    assertThat(service.findById(owned.getId())).isEmpty();
    assertThatThrownBy(() -> service.getViewById(owned.getId()))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.getViewByProductId(tenantA.yarnProductId()))
        .isInstanceOf(NotFoundException.class);
    assertThat(service.findViewByUid(owned.getUid())).isEmpty();
    assertThat(service.findViewsByName(owned.getName())).isEmpty();
    assertThat(service.list(null, null, null, null, PageRequest.of(0, 20)).getContent())
        .noneMatch(row -> row.id().equals(owned.getId()));
    assertThatThrownBy(() -> service.history(owned.getId(), PageRequest.of(0, 20)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.historyVersion(owned.getId(), 1))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.updateSpec(owned.getId(), command(tenantB, "30", "tenant B")))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.activate(owned.getId())).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.duplicateCandidates(owned.getId()))
        .isInstanceOf(NotFoundException.class);
    assertThat(
            aiToolProvider.execute(
                tenantB.tenantId(), "search_yarns", Map.of("query", "Integration yarn")))
        .doesNotContain(owned.getUid());
    assertThat(
            aiToolProvider.execute(
                tenantB.tenantId(), "get_yarn_info", Map.of("uid", owned.getUid())))
        .contains("not found");

    YarnArticle independent = create(tenantB);
    assertThat(independent.getTenantId()).isEqualTo(tenantB.tenantId());
    assertThat(independent.getProductId()).isEqualTo(tenantB.yarnProductId());
  }

  @Test
  void aiCreateCapturesDesignationAndLeavesEveryCanonicalSpecFieldUnset() {
    Fixture fixture = insertFixture("ai-draft-capture");
    use(fixture);

    String response =
        aiToolProvider.execute(
            fixture.tenantId(),
            "create_yarn_article",
            Map.of(
                "name", "Supplier draft shell",
                "unit", "kg",
                "sourceDesignation", "Ne 30/2"));

    var created =
        service
            .list(null, "Supplier draft shell", null, null, PageRequest.of(0, 10))
            .getContent()
            .getFirst();
    var article = service.getViewById(created.id());
    assertThat(response).contains("Status: DRAFT", "Canonical fields await human confirmation");
    assertThat(article.sourceDesignation()).isEqualTo("Ne 30/2");
    assertThat(article.originalCountSystem()).isNull();
    assertThat(article.originalCountValue()).isNull();
    assertThat(article.countBasis()).isNull();
    assertThat(article.structureType()).isNull();
    assertThat(article.foldCount()).isNull();
    assertThat(article.filamentCount()).isNull();
    assertThat(article.twistContractionPercent()).isNull();
    assertThat(article.resultantLinearDensityTex()).isNull();
    assertThat(article.canonicalDesignation()).isNull();
    assertThat(article.materialForm()).isNull();
    assertThat(article.spinningTechnologyFamily()).isNull();
    assertThat(article.spinningSystem()).isNull();
    assertThat(article.filamentForm()).isNull();
    assertThat(article.canonicalKey()).isNull();
    assertThat(article.constructionFeatures()).isEmpty();
    assertThat(article.composition()).isEmpty();
    assertThat(article.structureComponents()).isEmpty();
    assertThat(article.twistStages()).isEmpty();
    assertThat(article.productId()).isNotEqualTo(fixture.yarnProductId());
    assertThat(
            queryOne(
                "SELECT count(*) FROM production.prod_product "
                    + "WHERE tenant_id = ? AND id = ? AND product_type = 'YARN'",
                Long.class,
                fixture.tenantId(),
                article.productId()))
        .isEqualTo(1L);
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

  @Test
  void mutationResponseNamesEarlierDuplicateAndIncompleteDraftHasNoCandidates() {
    Fixture fixture = insertFixture("duplicate-response");
    YarnArticle first = create(fixture);
    java.util.UUID secondProductId = insertYarnProduct(fixture);
    java.util.UUID incompleteProductId = insertYarnProduct(fixture);

    use(fixture);
    var duplicate =
        service.createDraftResponse(
            secondProductId,
            "Same identity",
            null,
            command(fixture, "20", "different supplier wording"));
    var incomplete =
        service.createDraftResponse(
            incompleteProductId, "Incomplete draft", null, YarnArticleSpecCommand.empty());

    assertThat(duplicate.duplicateCandidates())
        .extracting(candidate -> candidate.articleId())
        .containsExactly(first.getId());
    assertThat(incomplete.duplicateCandidates()).isEmpty();
    assertThat(service.duplicateCandidates(duplicate.article().id()))
        .extracting(candidate -> candidate.articleId())
        .containsExactly(first.getId());
    assertThat(service.duplicateCandidates(incomplete.article().id())).isEmpty();
    assertThat(service.findById(incomplete.article().id())).isPresent();
  }

  private java.util.UUID insertYarnProduct(Fixture fixture) {
    java.util.UUID productId = java.util.UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'YARN', 'KG', TRUE)",
              productId,
              fixture.tenantId(),
              "YAI-YPROD-" + java.util.UUID.randomUUID().toString().substring(0, 8));
          return null;
        });
    return productId;
  }
}

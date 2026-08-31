package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.yarn.domain.article.ComponentKind;
import com.fabricmanagement.product.yarn.domain.article.LayerRole;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAudit;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAuditEventType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class YarnArticleAuditReconstructionIT extends YarnArticleIntegrationSupport {

  @Test
  void rebuildsVersionsOneThroughFourFromSpecAfterAndAllowsNonSpecRowsAtVersionFour() {
    Fixture fixture = insertFixture("audit");
    YarnArticle article = create(fixture);
    service.updateSpec(article.getId(), command(fixture, "21", "21 tex supplier"));
    service.updateSpec(article.getId(), command(fixture, "22", "22 tex supplier"));
    service.updateSpec(article.getId(), command(fixture, "23", "23 tex supplier"));
    service.updateMetadata(article.getId(), "Renamed yarn", "metadata edit");
    service.activate(article.getId());

    List<YarnArticleAudit> audits =
        auditRepository.findByTenantIdAndArticle_IdOrderByCreatedAtAsc(
            fixture.tenantId(), article.getId());
    List<JsonNode> versions =
        audits.stream()
            .filter(
                row ->
                    row.getEventType() == YarnArticleAuditEventType.CREATED
                        || row.getEventType() == YarnArticleAuditEventType.SPEC_UPDATED)
            .sorted(Comparator.comparingInt(YarnArticleAudit::getSpecVersionTo))
            .map(YarnArticleAudit::getSpecAfter)
            .toList();

    assertThat(versions).hasSize(4);
    assertThat(versions)
        .extracting(node -> node.path("articleSpecVersion").asInt())
        .containsExactly(1, 2, 3, 4);
    assertThat(versions)
        .extracting(node -> node.path("sourceDesignation").asText())
        .containsExactly("20 tex", "21 tex supplier", "22 tex supplier", "23 tex supplier");
    assertThat(versions)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.path("spinningSystemRef").path("code").asText())
                  .isEqualTo("RING");
              assertThat(snapshot.path("twistStages").get(0).path("testMethodCode").asText())
                  .isEqualTo("ISO_2061");
              assertThat(snapshot.path("composition").get(0).path("fiberName").asText())
                  .isEqualTo("Tenant Cotton");
              assertThat(snapshot.path("composition").get(0).path("fiberIsoCode").asText())
                  .startsWith("C");
            });
    assertThat(audits)
        .filteredOn(row -> row.getSpecVersionTo() == 4)
        .extracting(YarnArticleAudit::getEventType)
        .containsExactlyInAnyOrder(
            YarnArticleAuditEventType.SPEC_UPDATED,
            YarnArticleAuditEventType.METADATA_UPDATED,
            YarnArticleAuditEventType.ACTIVATED);
    assertThat(audits).extracting(YarnArticleAudit::getCreatedBy).containsOnly(fixture.actorId());

    var history =
        service.history(
            article.getId(), PageRequest.of(0, 3, Sort.by(Sort.Direction.ASC, "createdAt")));
    assertThat(history.getContent()).hasSize(3);
    assertThat(history.getTotalElements()).isEqualTo(audits.size());
    assertThat(
            service
                .historyVersion(article.getId(), 3)
                .specAfter()
                .path("articleSpecVersion")
                .asInt())
        .isEqualTo(3);
  }

  @Test
  void partialUniqueIndexRejectsASecondSpecEventForOneVersion() {
    Fixture fixture = insertFixture("audit-unique");
    YarnArticle article = create(fixture);

    assertThatThrownBy(
            () ->
                systemTransactions.executeInTransaction(
                    jdbc -> {
                      jdbc.update(
                          "INSERT INTO production.prod_yarn_article_audit "
                              + "(tenant_id, uid, article_id, event_type, spec_version_from, "
                              + "spec_version_to, payload_schema_version, spec_after, changed_summary) "
                              + "VALUES (?, ?, ?, 'SPEC_UPDATED', 1, 1, 1, '{}'::jsonb, '{}'::jsonb)",
                          fixture.tenantId(),
                          "DUPLICATE-AUDIT",
                          article.getId());
                      return null;
                    }))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("uq_yarn_article_audit_spec_version");
  }

  @Test
  void deletingAndReaddingStaleCompositionRecopiesSourceAndAuditsOneSpecBump() {
    Fixture fixture = insertFixture("source-recopy");
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_fiber SET material_source = NULL WHERE id = ?",
              fixture.fiberId());
          return null;
        });
    YarnArticle article = create(fixture);
    assertThat(article.getComposition().getFirst().getMaterialSource()).isNull();

    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "UPDATE production.prod_fiber SET material_source = 'RECYCLED' WHERE id = ?",
              fixture.fiberId());
          return null;
        });
    YarnArticleSpecCommand base = command(fixture, "20", "recopied source");
    YarnArticleSpecCommand rewritten =
        new YarnArticleSpecCommand(
            base.originalCountSystem(),
            base.originalCountValue(),
            base.countBasis(),
            base.structureType(),
            base.foldCount(),
            base.filamentCount(),
            base.twistContractionPercent(),
            base.sourceDesignation(),
            base.materialForm(),
            base.spinningTechnologyFamily(),
            base.spinningSystemId(),
            base.filamentForm(),
            Set.of(YarnConstructionFeature.CORE_SPUN),
            base.composition(),
            List.of(
                new YarnArticleSpecCommand.ComponentCommand(
                    ComponentKind.LAYER, 1, LayerRole.CORE, null, null, fixture.fiberId(), "core"),
                new YarnArticleSpecCommand.ComponentCommand(
                    ComponentKind.LAYER,
                    2,
                    LayerRole.SHEATH,
                    null,
                    null,
                    fixture.fiberId(),
                    "sheath")),
            base.twistStages());

    YarnArticle updated = service.updateSpec(article.getId(), rewritten);

    assertThat(updated.getArticleSpecVersion()).isEqualTo(2);
    assertThat(updated.getComposition().getFirst().getMaterialSource().name())
        .isEqualTo("RECYCLED");
    List<YarnArticleAudit> specUpdates =
        auditRepository
            .findByTenantIdAndArticle_IdOrderByCreatedAtAsc(fixture.tenantId(), article.getId())
            .stream()
            .filter(row -> row.getEventType() == YarnArticleAuditEventType.SPEC_UPDATED)
            .toList();
    assertThat(specUpdates)
        .singleElement()
        .satisfies(
            audit -> {
              assertThat(audit.getSpecVersionFrom()).isEqualTo(1);
              assertThat(audit.getSpecVersionTo()).isEqualTo(2);
              assertThat(
                      audit
                          .getSpecAfter()
                          .path("composition")
                          .get(0)
                          .path("materialSource")
                          .asText())
                  .isEqualTo("RECYCLED");
            });
  }
}

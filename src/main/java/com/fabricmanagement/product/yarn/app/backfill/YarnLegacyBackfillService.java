package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.app.YarnArticleSpecCommand;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationDiscovery;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationRecord;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.app.port.LegacyYarnDesignationSource;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueReason;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillReconciliation;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillLockRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YarnLegacyBackfillService {

  static final int MAX_SOURCE_DESIGNATION_CODE_POINTS = 255;

  private final List<LegacyYarnDesignationSource> sources;
  private final DesignationProvenancePolicy provenancePolicy;
  private final ProductRepository productRepository;
  private final YarnArticleRepository articleRepository;
  private final YarnBackfillReconciliationRepository reconciliationRepository;
  private final YarnBackfillLockRepository lockRepository;
  private final YarnArticleService articleService;

  @Transactional
  public YarnLegacyBackfillReport backfillTenant(UUID tenantId) {
    requireCurrentTenant(tenantId);
    if (!lockRepository.tryAcquire(tenantId)) {
      return YarnLegacyBackfillReport.lockSkipped(tenantId);
    }

    List<Product> products =
        productRepository.findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.YARN);
    Set<UUID> existingArticleProductIds =
        products.isEmpty()
            ? Set.of()
            : articleRepository
                .findByTenantIdAndProduct_IdIn(
                    tenantId, products.stream().map(Product::getId).toList())
                .stream()
                .map(YarnArticle::getProductId)
                .collect(Collectors.toSet());
    Set<UUID> openQueueProductIds =
        reconciliationRepository
            .findByTenantIdAndStatus(tenantId, YarnBackfillQueueStatus.OPEN)
            .stream()
            .map(YarnBackfillReconciliation::getProductId)
            .collect(Collectors.toSet());

    CombinedDiscovery discovery = discover(tenantId);
    Map<UUID, List<LegacyDesignationRecord>> recordsByProduct =
        discovery.records().stream()
            .collect(Collectors.groupingBy(LegacyDesignationRecord::productId));

    long skipped = 0;
    long created = 0;
    long candidatesWritten = 0;
    EnumMap<YarnBackfillQueueReason, Long> queueCreated =
        new EnumMap<>(YarnBackfillQueueReason.class);

    for (Product product : products) {
      if (existingArticleProductIds.contains(product.getId())) {
        skipped++;
        continue;
      }

      Selection selection = select(recordsByProduct.getOrDefault(product.getId(), List.of()));
      YarnArticle article =
          articleService.createDraft(
              product,
              product.getUid(),
              null,
              YarnArticleSpecCommand.draftCapture(selection.chosenRawValue()));
      created++;
      if (selection.chosenRawValue() != null) {
        candidatesWritten++;
      }

      if (selection.reason() != null && !openQueueProductIds.contains(product.getId())) {
        reconciliationRepository.save(
            YarnBackfillReconciliation.open(
                product, article, selection.reason(), selection.candidates()));
        openQueueProductIds.add(product.getId());
        queueCreated.merge(selection.reason(), 1L, Long::sum);
      }
    }

    // One persistence-context flush for all article, audit, and queue writes in this tenant.
    articleService.flushDrafts();
    return new YarnLegacyBackfillReport(
        tenantId,
        YarnLegacyBackfillOutcome.COMPLETED,
        products.size(),
        skipped,
        created,
        candidatesWritten,
        queueCreated,
        discovery.recordsContributed(),
        discovery.unlinkedCounts());
  }

  private CombinedDiscovery discover(UUID tenantId) {
    List<LegacyDesignationRecord> records = new ArrayList<>();
    EnumMap<LegacyDesignationSourceKind, Long> contributed =
        new EnumMap<>(LegacyDesignationSourceKind.class);
    EnumMap<LegacyDesignationSourceKind, Long> unlinked =
        new EnumMap<>(LegacyDesignationSourceKind.class);
    for (LegacyYarnDesignationSource source : sources) {
      LegacyDesignationDiscovery discovery = source.discover(tenantId);
      if (discovery == null) {
        continue;
      }
      records.addAll(discovery.records());
      discovery.records().stream()
          .filter(record -> record.rawValue() != null && !record.rawValue().isBlank())
          .forEach(record -> contributed.merge(record.sourceKind(), 1L, Long::sum));
      discovery.unlinkedCounts().forEach((kind, count) -> unlinked.merge(kind, count, Long::sum));
    }
    return new CombinedDiscovery(records, contributed, unlinked);
  }

  private Selection select(List<LegacyDesignationRecord> discovered) {
    List<LegacyDesignationRecord> candidates =
        discovered.stream()
            .filter(record -> record.rawValue() != null && !record.rawValue().isBlank())
            .toList();
    List<LegacyDesignationRecord> sortedCandidates = provenancePolicy.sorted(candidates);
    Map<String, List<LegacyDesignationRecord>> shortGroups = new LinkedHashMap<>();
    boolean hasOverlength = false;
    for (LegacyDesignationRecord record : candidates) {
      if (isOverlength(record.rawValue())) {
        hasOverlength = true;
        continue;
      }
      String normalized = record.rawValue().trim().toLowerCase(Locale.ROOT);
      shortGroups.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(record);
    }

    YarnBackfillQueueReason reason =
        shortGroups.size() > 1
            ? YarnBackfillQueueReason.AMBIGUOUS
            : hasOverlength ? YarnBackfillQueueReason.OVERLENGTH : null;
    String chosenRawValue =
        shortGroups.size() == 1 && !hasOverlength
            ? provenancePolicy.preferred(shortGroups.values().iterator().next()).rawValue()
            : null;
    return new Selection(chosenRawValue, reason, candidatePayload(sortedCandidates));
  }

  private JsonNode candidatePayload(List<LegacyDesignationRecord> sortedCandidates) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("schemaVersion", 1);
    ArrayNode candidates = root.putArray("candidates");
    for (LegacyDesignationRecord record : sortedCandidates) {
      ObjectNode candidate = candidates.addObject();
      candidate.put("rawValue", record.rawValue());
      candidate.put("sourceKind", record.sourceKind().name());
      candidate.put("recordedAt", record.recordedAt().toString());
      candidate.put("sourceRecordId", record.sourceRecordId());
      candidate.put("overlength", isOverlength(record.rawValue()));
    }
    return root;
  }

  private boolean isOverlength(String rawValue) {
    return rawValue.codePointCount(0, rawValue.length()) > MAX_SOURCE_DESIGNATION_CODE_POINTS;
  }

  private void requireCurrentTenant(UUID tenantId) {
    if (tenantId == null || !tenantId.equals(TenantContext.requireTenantId())) {
      throw new IllegalArgumentException("Backfill tenant must match TenantContext");
    }
  }

  private record CombinedDiscovery(
      List<LegacyDesignationRecord> records,
      Map<LegacyDesignationSourceKind, Long> recordsContributed,
      Map<LegacyDesignationSourceKind, Long> unlinkedCounts) {}

  private record Selection(
      String chosenRawValue, YarnBackfillQueueReason reason, JsonNode candidates) {}
}

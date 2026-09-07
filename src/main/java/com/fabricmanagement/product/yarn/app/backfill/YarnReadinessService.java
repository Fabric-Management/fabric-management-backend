package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.yarn.app.port.YarnUsageDiscovery;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignalSource;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.dto.YarnReadinessBlockerDto;
import com.fabricmanagement.product.yarn.dto.YarnReadinessDto;
import com.fabricmanagement.product.yarn.dto.YarnUnlinkedOpenDocumentsDto;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YarnReadinessService {

  private final List<YarnUsageSignalSource> usageSources;
  private final ProductRepository productRepository;
  private final YarnArticleRepository articleRepository;
  private final YarnBackfillReconciliationRepository reconciliationRepository;

  @Transactional(readOnly = true)
  public YarnReadinessDto readiness(int blockersLimit) {
    UUID tenantId = TenantContext.requireTenantId();
    EnumMap<YarnUsageSignal, Set<UUID>> referenced = new EnumMap<>(YarnUsageSignal.class);
    EnumMap<YarnUsageSignal, Long> unlinked = new EnumMap<>(YarnUsageSignal.class);
    for (YarnUsageSignalSource source : usageSources) {
      YarnUsageDiscovery discovery = source.discover(tenantId);
      discovery
          .referencedProductIds()
          .forEach(
              (signal, ids) ->
                  referenced.computeIfAbsent(signal, ignored -> new HashSet<>()).addAll(ids));
      discovery
          .unlinkedYarnDocumentCounts()
          .forEach((signal, count) -> unlinked.merge(signal, count, Long::sum));
    }

    Set<UUID> allReferencedIds =
        referenced.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    List<Product> yarnProducts =
        allReferencedIds.isEmpty()
            ? List.of()
            : productRepository.findByTenantIdAndIdInAndProductType(
                tenantId, allReferencedIds, ProductType.YARN);
    List<UUID> yarnProductIds = yarnProducts.stream().map(Product::getId).toList();
    Map<UUID, YarnArticle> articlesByProduct =
        yarnProductIds.isEmpty()
            ? Map.of()
            : articleRepository.findByTenantIdAndProduct_IdIn(tenantId, yarnProductIds).stream()
                .collect(Collectors.toMap(YarnArticle::getProductId, Function.identity()));

    List<YarnReadinessBlockerDto> allBlockers = new ArrayList<>();
    for (Product product : yarnProducts) {
      YarnArticle article = articlesByProduct.get(product.getId());
      if (article != null && article.getStatus() == YarnArticleStatus.ACTIVE) {
        continue;
      }
      List<YarnUsageSignal> signals =
          referenced.entrySet().stream()
              .filter(entry -> entry.getValue().contains(product.getId()))
              .map(Map.Entry::getKey)
              .sorted()
              .toList();
      allBlockers.add(
          new YarnReadinessBlockerDto(
              product.getId(),
              product.getUid(),
              article == null ? null : article.getId(),
              article == null ? null : article.getStatus(),
              signals));
    }
    allBlockers.sort(
        Comparator.comparing(YarnReadinessBlockerDto::productUid)
            .thenComparing(YarnReadinessBlockerDto::productId));

    long blockerCount = allBlockers.size();
    return new YarnReadinessDto(
        blockerCount == 0,
        yarnProducts.size(),
        blockerCount,
        reconciliationRepository.countByTenantIdAndStatus(tenantId, YarnBackfillQueueStatus.OPEN),
        new YarnUnlinkedOpenDocumentsDto(
            unlinked.getOrDefault(YarnUsageSignal.OPEN_PURCHASE_ORDER, 0L),
            unlinked.getOrDefault(YarnUsageSignal.OPEN_WORK_ORDER, 0L)),
        productRepository.countByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.YARN),
        articleRepository.countByTenantIdAndStatus(tenantId, YarnArticleStatus.ACTIVE),
        YarnUsageSignalSource.MOVEMENT_WINDOW_DAYS,
        allBlockers.stream().limit(blockersLimit).toList());
  }
}

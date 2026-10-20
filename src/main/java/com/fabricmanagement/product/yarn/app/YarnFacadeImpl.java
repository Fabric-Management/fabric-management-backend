package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.dto.YarnArticleSummaryDto;
import com.fabricmanagement.product.yarn.dto.YarnCompositionLineDto;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YarnFacadeImpl implements YarnFacade {

  private final YarnArticleRepository articleRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<YarnArticleSummaryDto> findByProductId(UUID productId) {
    return articleRepository
        .findByTenantIdAndProduct_Id(TenantContext.requireTenantId(), productId)
        .map(YarnArticleSummaryDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public List<YarnArticleSummaryDto> findByProductIds(List<UUID> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return List.of();
    }
    return articleRepository
        .findByTenantIdAndProduct_IdIn(TenantContext.requireTenantId(), productIds)
        .stream()
        .map(YarnArticleSummaryDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID productId) {
    return articleRepository.existsByTenantIdAndProduct_Id(
        TenantContext.requireTenantId(), productId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<YarnCompositionLineDto>> findDeclaredCompositions(
      Collection<UUID> productIds) {
    if (productIds == null || productIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<YarnCompositionLineDto>> result = new LinkedHashMap<>();
    for (YarnArticle article :
        articleRepository.findByTenantIdAndProduct_IdIn(
            TenantContext.requireTenantId(), productIds)) {
      List<YarnCompositionLineDto> lines =
          article.getComposition().stream()
              .map(
                  row ->
                      new YarnCompositionLineDto(
                          row.getFiberId(),
                          row.getFiberIsoCode(),
                          row.getFiberName(),
                          row.getMaterialSource(),
                          row.getPercentage()))
              .toList();
      if (!lines.isEmpty()) {
        result.put(article.getProductId(), lines);
      }
    }
    return result;
  }
}

package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fabric.api.facade.FabricFacade;
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
import com.fabricmanagement.product.fabric.infra.repository.FabricConstructionRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class FabricFacadeImpl implements FabricFacade {

  private final FabricArticleService articles;
  private final FabricConstructionRepository constructions;

  @Override
  @Transactional
  public FabricArticleDto createArticle(FabricArticleRequest request, boolean activate) {
    FabricArticleDto created = articles.create(request);
    return activate ? articles.activate(created.id()) : created;
  }

  @Override
  @Transactional
  public FabricArticleDto ensureArticle(
      UUID productId, FabricArticleRequest request, boolean activate) {
    Optional<FabricArticleDto> existing = findArticleByProduct(productId);
    if (existing.isPresent()) {
      return existing.get();
    }
    FabricArticleDto attached = articles.attach(productId, request);
    return activate ? articles.activate(attached.id()) : attached;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FabricArticleDto> findArticleByProduct(UUID productId) {
    return articles.findByProduct(productId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FabricConstructionDto> findConstruction(String code) {
    return constructions
        .findByTenantIdAndCodeAndDeletedAtIsNull(TenantContext.requireTenantId(), code)
        .filter(construction -> construction.isUsable())
        .map(FabricConstructionDto::from);
  }
}

package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.fabric.api.facade.FabricFacade;
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import com.fabricmanagement.product.fabric.infra.repository.FabricConstructionRepository;
import com.fabricmanagement.product.fabric.infra.repository.FabricFinishingProcessRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class FabricFacadeImpl implements FabricFacade {

  private final FabricArticleService articles;
  private final FabricConstructionRepository constructions;
  private final FabricFinishingProcessRepository finishingProcesses;
  private final FabricFinishingPriceService finishingPrices;

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

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, FabricFinishingProcessDto> findFinishingProcesses(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> wanted = Set.copyOf(ids);
    return finishingProcesses.findByTenantIdAndDeletedAtIsNull(tenantId).stream()
        .filter(process -> wanted.contains(process.getId()))
        .map(FabricFinishingProcessDto::from)
        .collect(Collectors.toMap(FabricFinishingProcessDto::id, Function.identity()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FabricFinishingProcessDto> findUsableFinishingProcess(UUID id) {
    return finishingProcesses
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.requireTenantId(), id)
        .filter(process -> process.isUsable())
        .map(FabricFinishingProcessDto::from);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<FabricFinishingPriceDto> findFinishingPrice(
      UUID processId, UUID partnerId, LocalDate date) {
    return finishingPrices.inForce(processId, partnerId, date);
  }
}

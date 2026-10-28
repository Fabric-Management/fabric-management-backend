package com.fabricmanagement.product.fabric.api.facade;

import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-process fabric master data for other modules and the playground seed. */
public interface FabricFacade {

  /** Creates the article and its FABRIC product; activates it when {@code activate} is set. */
  FabricArticleDto createArticle(FabricArticleRequest request, boolean activate);

  /**
   * The product's article, created from {@code request} when it has none yet (idempotent); a new
   * one is activated when {@code activate} is set.
   */
  FabricArticleDto ensureArticle(UUID productId, FabricArticleRequest request, boolean activate);

  Optional<FabricArticleDto> findArticleByProduct(UUID productId);

  /** A usable construction of the current tenant by its code. */
  Optional<FabricConstructionDto> findConstruction(String code);

  /**
   * The current tenant's finishing processes by id, deactivated ones included, so the history of a
   * lot stays readable after the dictionary changes. Unknown ids are absent from the result.
   */
  Map<UUID, FabricFinishingProcessDto> findFinishingProcesses(Collection<UUID> ids);

  /** A finishing process of the current tenant that may still be recorded on a lot. */
  Optional<FabricFinishingProcessDto> findUsableFinishingProcess(UUID id);

  /**
   * What a finishing process costs at a firm on a date: the firm's own latest tariff that has
   * started, else the latest general price that has started. {@code partnerId} is null for work at
   * an own site. Empty when nothing is priced.
   */
  Optional<FabricFinishingPriceDto> findFinishingPrice(
      UUID processId, UUID partnerId, LocalDate date);
}

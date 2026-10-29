package com.fabricmanagement.product.fabric.api.facade;

import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceClassDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.fabric.dto.FabricFinishingProcessDto;
import com.fabricmanagement.product.fabric.dto.FinishingPriceQuery;
import java.util.Collection;
import java.util.List;
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
   * The finishing prices in force for a piece of work: the firm's own tariff when it has one that
   * fits the lot, else the general prices, with one price per quality grade the firm prices
   * separately. The price without a grade comes first and covers every grade without a price of its
   * own; a grade with neither is not billed. Empty when nothing is priced.
   */
  List<FabricFinishingPriceDto> findFinishingPrices(FinishingPriceQuery query);

  /** A finishing price class of the current tenant that may still be chosen for a step. */
  Optional<FabricFinishingPriceClassDto> findUsablePriceClass(UUID id);

  /**
   * The current tenant's finishing price classes by id, retired ones included, so the history of a
   * lot stays readable. Unknown ids are absent from the result.
   */
  Map<UUID, FabricFinishingPriceClassDto> findPriceClasses(Collection<UUID> ids);
}

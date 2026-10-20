package com.fabricmanagement.product.fabric.api.facade;

import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricConstructionDto;
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
}

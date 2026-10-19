package com.fabricmanagement.product.yarn.api.facade;

import com.fabricmanagement.product.yarn.dto.YarnArticleSummaryDto;
import com.fabricmanagement.product.yarn.dto.YarnCompositionLineDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Supported in-process yarn article lookup boundary for other product submodules. */
public interface YarnFacade {

  Optional<YarnArticleSummaryDto> findByProductId(UUID productId);

  List<YarnArticleSummaryDto> findByProductIds(List<UUID> productIds);

  boolean exists(UUID productId);

  /**
   * The declared fibre composition of each given yarn product that has one, keyed by product id. A
   * yarn product without a yarn article or without composition is absent from the map.
   */
  Map<UUID, List<YarnCompositionLineDto>> findDeclaredCompositions(Collection<UUID> productIds);
}

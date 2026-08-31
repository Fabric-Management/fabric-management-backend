package com.fabricmanagement.product.yarn.api.facade;

import com.fabricmanagement.product.yarn.dto.YarnArticleSummaryDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Supported in-process yarn article lookup boundary for other product submodules. */
public interface YarnFacade {

  Optional<YarnArticleSummaryDto> findByProductId(UUID productId);

  List<YarnArticleSummaryDto> findByProductIds(List<UUID> productIds);

  boolean exists(UUID productId);
}

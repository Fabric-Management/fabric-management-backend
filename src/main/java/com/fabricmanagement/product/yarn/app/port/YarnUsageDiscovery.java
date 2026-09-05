package com.fabricmanagement.product.yarn.app.port;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record YarnUsageDiscovery(
    Map<YarnUsageSignal, Set<UUID>> referencedProductIds,
    Map<YarnUsageSignal, Long> unlinkedYarnDocumentCounts) {

  public YarnUsageDiscovery {
    referencedProductIds =
        referencedProductIds == null ? Map.of() : Map.copyOf(referencedProductIds);
    unlinkedYarnDocumentCounts =
        unlinkedYarnDocumentCounts == null ? Map.of() : Map.copyOf(unlinkedYarnDocumentCounts);
  }
}

package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.domain.Batch;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Dynamic, index-friendly batch population queries for stock availability. */
public interface StockAvailabilityBatchRepository {

  Page<ProductRow> findAvailabilityProducts(UUID tenantId, Filter filter, Pageable pageable);

  Page<Batch> findAvailabilityLots(UUID tenantId, Filter filter, Pageable pageable);

  List<Batch> findAvailabilityBatchesForProducts(
      UUID tenantId, Filter filter, Collection<UUID> productIds);

  record Filter(
      UUID colorId,
      boolean colourless,
      UUID productId,
      UUID batchId,
      UUID qualityGradeId,
      boolean qualityUnassigned) {}

  record ProductRow(UUID productId, ProductType productType) {}
}

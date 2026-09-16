package com.fabricmanagement.product.core.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned catalog facts, independent of whether a product currently has stock. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductEvidenceQueryService {
  private final ProductRepository repository;

  public List<Reference> findReferences(Collection<UUID> productIds) {
    if (productIds.isEmpty()) return List.of();
    return repository.findEvidenceReferences(TenantContext.requireTenantId(), productIds).stream()
        .map(
            row ->
                new Reference(
                    row.getId(),
                    row.getProductType(),
                    row.getRevision(),
                    row.getRecordedAt(),
                    Boolean.TRUE.equals(row.getActive())))
        .toList();
  }

  public record Reference(
      UUID id, ProductType productType, Long revision, Instant recordedAt, boolean active) {}
}

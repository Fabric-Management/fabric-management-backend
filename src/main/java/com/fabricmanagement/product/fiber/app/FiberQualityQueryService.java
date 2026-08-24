package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberQualityStandard;
import com.fabricmanagement.product.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read boundary for production consumers of fiber quality-reference data. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiberQualityQueryService {

  private final FiberRepository fiberRepository;
  private final FiberQualityStandardRepository qualityStandardRepository;

  public Optional<Fiber> findByProductId(UUID productId) {
    return fiberRepository.findByProductId(productId);
  }

  /** Preserves the legacy lookup fallback for batches that stored a fiber id as product id. */
  public Optional<Fiber> findByProductIdOrId(UUID productId) {
    Optional<Fiber> fiber = fiberRepository.findByProductId(productId);
    return fiber.isPresent() ? fiber : fiberRepository.findById(productId);
  }

  public Optional<FiberQualityStandard> findQualityStandardById(
      UUID tenantId, UUID qualityStandardId) {
    return qualityStandardRepository.findByTenantIdAndId(tenantId, qualityStandardId);
  }

  public Optional<FiberQualityStandard> findDefaultQualityStandard(UUID tenantId, UUID isoCodeId) {
    return qualityStandardRepository.findByTenantIdAndIsoCode_IdAndIsDefaultTrueAndIsActiveTrue(
        tenantId, isoCodeId);
  }
}

package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.product.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberCertificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-module read service for the shared certification-scheme dictionary. Every lookup is scoped
 * to the catalogue owner (FIBER-CATALOG-1); a scheme conveys no certification by itself.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiberCertificationQueryService {

  private final FiberCertificationRepository repository;

  public FiberCertificationDto findActiveByIdOrThrow(UUID id) {
    return findActiveEntityById(id)
        .map(FiberCertificationDto::from)
        .orElseThrow(() -> new NotFoundException("FiberCertification not found: " + id));
  }

  /** Returns the managed reference used by cross-module JPA associations. */
  public Optional<FiberCertification> findActiveEntityById(UUID id) {
    return id == null
        ? Optional.empty()
        : repository.findByTenantIdAndIdAndIsActiveTrue(FiberCatalog.OWNER_ID, id);
  }

  /** Active shared scheme by code, e.g. {@code GOTS}. */
  public Optional<FiberCertification> findActiveEntityByCode(String certificationCode) {
    return repository.findByTenantIdAndCertificationCodeAndIsActiveTrue(
        FiberCatalog.OWNER_ID, certificationCode);
  }

  public List<FiberCertificationDto> findAllActiveByIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return repository.findAllByTenantIdAndIdInAndIsActiveTrue(FiberCatalog.OWNER_ID, ids).stream()
        .map(FiberCertificationDto::from)
        .toList();
  }

  public boolean existsActiveById(UUID id) {
    return findActiveEntityById(id).isPresent();
  }
}

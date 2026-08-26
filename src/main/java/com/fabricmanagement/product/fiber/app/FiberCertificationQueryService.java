package com.fabricmanagement.product.fiber.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.product.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.product.fiber.infra.repository.FiberCertificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Cross-module read service that keeps FiberCertificationRepository private to product. */
@Service
@RequiredArgsConstructor
public class FiberCertificationQueryService {

  private final FiberCertificationRepository repository;

  public FiberCertificationDto findActiveByIdOrThrow(UUID id) {
    return repository
        .findByIdAndIsActiveTrue(id)
        .map(FiberCertificationDto::from)
        .orElseThrow(() -> new NotFoundException("FiberCertification not found: " + id));
  }

  /** Returns the managed reference used by cross-module JPA associations. */
  public Optional<FiberCertification> findActiveEntityById(UUID id) {
    return repository.findByIdAndIsActiveTrue(id);
  }

  public List<FiberCertificationDto> findAllActiveByIds(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return repository.findAllByIdInAndIsActiveTrue(ids).stream()
        .map(FiberCertificationDto::from)
        .toList();
  }

  public boolean existsActiveById(UUID id) {
    return repository.findByIdAndIsActiveTrue(id).isPresent();
  }
}

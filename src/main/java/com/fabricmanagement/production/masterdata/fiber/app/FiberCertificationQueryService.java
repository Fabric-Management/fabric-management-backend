package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Cross-module okuma servisi — platform modüllerinin FiberCertificationRepository infra katmanına
 * doğrudan erişmesini önler (Rule 13.2).
 */
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

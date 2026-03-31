package com.fabricmanagement.production.masterdata.fiber.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCertification;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
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

  public FiberCertification findActiveByIdOrThrow(UUID id) {
    return repository
        .findByIdAndIsActiveTrue(id)
        .orElseThrow(() -> new NotFoundException("FiberCertification not found: " + id));
  }

  public boolean existsActiveById(UUID id) {
    return repository.findByIdAndIsActiveTrue(id).isPresent();
  }
}

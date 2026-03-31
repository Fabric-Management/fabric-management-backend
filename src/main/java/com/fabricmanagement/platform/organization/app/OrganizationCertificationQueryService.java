package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.platform.organization.domain.OrganizationCertification;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationCertificationRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Cross-module okuma servisi — production modülünün OrganizationCertificationRepository infra
 * katmanına doğrudan erişmesini önler (Rule 13.1).
 */
@Service
@RequiredArgsConstructor
public class OrganizationCertificationQueryService {

  private final OrganizationCertificationRepository repository;

  public Optional<OrganizationCertification> findById(UUID orgCertId) {
    return repository.findById(orgCertId);
  }
}

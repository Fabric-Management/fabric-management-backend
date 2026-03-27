package com.fabricmanagement.platform.organization.infra.repository;

import com.fabricmanagement.platform.organization.domain.OrganizationCertification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationCertificationRepository
    extends JpaRepository<OrganizationCertification, UUID> {

  List<OrganizationCertification> findByOrganizationIdAndIsActiveTrue(UUID organizationId);

  List<OrganizationCertification> findByOrganizationId(UUID organizationId);
}

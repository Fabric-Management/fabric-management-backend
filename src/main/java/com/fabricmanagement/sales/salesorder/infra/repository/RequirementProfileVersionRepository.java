package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.RequirementProfileVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementProfileVersionRepository
    extends JpaRepository<RequirementProfileVersion, UUID> {

  Optional<RequirementProfileVersion> findByTenantIdAndProfileIdAndProfileVersion(
      UUID tenantId, UUID profileId, int profileVersion);
}

package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.platform.user.domain.JobTitlePreset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTitlePresetRepository extends JpaRepository<JobTitlePreset, UUID> {

  List<JobTitlePreset> findByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<JobTitlePreset> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<JobTitlePreset> findByTenantIdAndJobTitleCode(UUID tenantId, String jobTitleCode);

  boolean existsByTenantIdAndJobTitleCode(UUID tenantId, String jobTitleCode);

  List<JobTitlePreset> findByTenantIdAndDepartmentCodeAndIsActiveTrue(
      UUID tenantId, String departmentCode);
}

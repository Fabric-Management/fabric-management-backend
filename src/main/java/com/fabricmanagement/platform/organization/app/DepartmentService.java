package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DepartmentService {

  private final DepartmentRepository departmentRepository;

  public List<Department> getActiveDepartmentsByTenant(UUID tenantId) {
    return departmentRepository.findByTenantIdAndIsActiveTrue(tenantId);
  }

  public List<Department> getActiveDepartmentsByOrganization(UUID tenantId, UUID organizationId) {
    return departmentRepository.findByTenantIdAndOrganizationIdAndIsActiveTrue(
        tenantId, organizationId);
  }

  public Optional<Department> getDepartmentByTenantAndId(UUID tenantId, UUID id) {
    return departmentRepository.findByTenantIdAndId(tenantId, id);
  }
}

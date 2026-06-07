package com.fabricmanagement.human.core.employee.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.human.core.employee.infra.repository.EmployeeRepository;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmployeeProjectionAdapter implements EmployeeProjectionPort {

  private final EmployeeRepository employeeRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<EmployeeSnapshot> findByUserId(UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    return employeeRepository
        .findByTenantIdAndUserId(tenantId, userId)
        .map(EmployeeSnapshotFactory::fromEntity);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<EmployeeSnapshot> findByUserId(UUID tenantId, UUID userId) {
    return employeeRepository
        .findByTenantIdAndUserId(tenantId, userId)
        .map(EmployeeSnapshotFactory::fromEntity);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, EmployeeSnapshot> findByUserIds(UUID tenantId, Collection<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    return employeeRepository.findByTenantIdAndUserIdIn(tenantId, userIds).stream()
        .collect(Collectors.toMap(Employee::getUserId, EmployeeSnapshotFactory::fromEntity));
  }
}

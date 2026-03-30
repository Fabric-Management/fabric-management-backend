package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read port for employee data projection. Implemented in human/employee; defined in platform/user.
 */
public interface EmployeeProjectionPort {

  Optional<EmployeeSnapshot> findByUserId(UUID userId);

  Optional<EmployeeSnapshot> findByUserId(UUID tenantId, UUID userId);

  Map<UUID, EmployeeSnapshot> findByUserIds(UUID tenantId, Collection<UUID> userIds);
}

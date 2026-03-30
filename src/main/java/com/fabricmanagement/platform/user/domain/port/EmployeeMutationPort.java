package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.platform.user.domain.EmployeeFieldUpdates;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.util.Optional;
import java.util.UUID;

/** Partial update of an existing employee row when the user already has an HR record. */
public interface EmployeeMutationPort {

  Optional<EmployeeSnapshot> applyFieldUpdates(UUID userId, EmployeeFieldUpdates updates);
}

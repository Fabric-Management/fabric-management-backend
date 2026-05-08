package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;

/** Write port for employee lifecycle operations triggered from user management. */
public interface EmployeeCreationPort {

  EmployeeSnapshot createOrUpdate(EmployeeCreationCommand command);

  String generateEmployeeNumber();
}

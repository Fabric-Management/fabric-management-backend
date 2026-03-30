package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.time.LocalDate;
import java.util.UUID;

/** Write port for employee lifecycle operations triggered from user management. */
public interface EmployeeCreationPort {

  EmployeeSnapshot createOrUpdate(
      UUID userId,
      Title title,
      Gender gender,
      LocalDate birthDate,
      String nationality,
      String employeeNumber,
      LocalDate hireDate,
      EmergencyContactData emergencyContact);

  String generateEmployeeNumber();
}

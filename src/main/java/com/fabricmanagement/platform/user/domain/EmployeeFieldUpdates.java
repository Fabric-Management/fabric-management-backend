package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import java.time.LocalDate;

/** Non-null fields are applied when patching an existing {@link EmployeeSnapshot} / HR record. */
public record EmployeeFieldUpdates(
    Title title,
    Gender gender,
    LocalDate birthDate,
    String nationality,
    String employeeNumber,
    LocalDate hireDate,
    EmergencyContactData emergencyContact) {

  public boolean hasAny() {
    return title != null
        || gender != null
        || birthDate != null
        || nationality != null
        || employeeNumber != null
        || hireDate != null
        || emergencyContact != null;
  }
}

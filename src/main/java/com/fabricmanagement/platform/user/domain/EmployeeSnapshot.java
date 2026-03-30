package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import java.time.LocalDate;
import java.util.UUID;

/** Read-only projection of employee (HR) data for user enrichment — no human module entity. */
public record EmployeeSnapshot(
    UUID userId,
    Title title,
    Gender gender,
    LocalDate birthDate,
    String nationality,
    String employeeNumber,
    LocalDate hireDate,
    EmergencyContactData emergencyContact) {

  public static EmployeeSnapshot absent() {
    return new EmployeeSnapshot(null, null, null, null, null, null, null, null);
  }

  public boolean isPresent() {
    return userId != null;
  }
}

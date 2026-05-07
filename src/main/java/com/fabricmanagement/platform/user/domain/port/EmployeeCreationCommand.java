package com.fabricmanagement.platform.user.domain.port;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable command object for employee creation/update operations. Replaces the long parameter
 * list on {@link EmployeeCreationPort#createOrUpdate}.
 */
public record EmployeeCreationCommand(
    UUID userId,
    Title title,
    Gender gender,
    LocalDate birthDate,
    String nationality,
    String employeeNumber,
    LocalDate hireDate,
    EmergencyContactData emergencyContact,
    UUID jobTitlePresetId) {}

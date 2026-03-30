package com.fabricmanagement.platform.user.domain.port;

import java.util.List;
import java.util.UUID;

/** Triggers HR compliance evaluation after user/employee changes (human module implementation). */
public interface EmployeeCompliancePort {

  /** Missing recommended fields, or empty if no employee or all complete. */
  List<String> runComplianceEvaluation(UUID userId, String departmentName);
}

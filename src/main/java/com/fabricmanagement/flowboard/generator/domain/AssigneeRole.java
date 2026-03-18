package com.fabricmanagement.flowboard.generator.domain;

/**
 * SmartTaskGenerator'ın otomatik task atarken kullandığı rol tipi.
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — TaskTemplate.defaultAssigneeRole
 */
public enum AssigneeRole {

  /** Departman adminine ata */
  DEPARTMENT_ADMIN,

  /** Manager'a ata */
  MANAGER,

  /** Müsait herhangi birine ata */
  ANY
}

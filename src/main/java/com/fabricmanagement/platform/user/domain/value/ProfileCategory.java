package com.fabricmanagement.platform.user.domain.value;

/**
 * Profile category for permission checks.
 *
 * <p>Separates work-related information from personal information for proper access control.
 */
public enum ProfileCategory {

  /**
   * Work profile: Name, work email, work phone, work address, department. Can be updated by Admin,
   * HR Manager, or Department Manager.
   */
  WORK_PROFILE,

  /**
   * Personal profile: Home address, personal phone, birth date, emergency contact. Can only be
   * updated by Admin or HR Manager.
   */
  PERSONAL_PROFILE
}

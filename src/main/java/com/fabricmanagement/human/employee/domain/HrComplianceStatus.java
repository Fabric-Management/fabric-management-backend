package com.fabricmanagement.human.employee.domain;

/**
 * HR Compliance Status - Tracks employee record completeness.
 * 
 * <p><b>Status Levels:</b></p>
 * <ul>
 *   <li><b>COMPLETE</b> - All recommended HR fields are present</li>
 *   <li><b>MISSING_RECOMMENDED</b> - Some recommended fields are missing (employeeNumber, hireDate, department, emergencyContact)</li>
 *   <li><b>MISSING_REQUIRED</b> - Critical required fields are missing (should not happen in normal flow)</li>
 * </ul>
 */
public enum HrComplianceStatus {
    COMPLETE,
    MISSING_RECOMMENDED,
    MISSING_REQUIRED
}


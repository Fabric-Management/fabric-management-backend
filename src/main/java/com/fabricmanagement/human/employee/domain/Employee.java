package com.fabricmanagement.human.employee.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

/**
 * Employee entity - HR/İK data for platform users.
 * 
 * <p><b>CRITICAL DESIGN:</b> One-to-One relationship with User entity.</p>
 * <ul>
 *   <li>✅ User = Authentication, basic identity, platform access</li>
 *   <li>✅ Employee = HR data, personal info, employment details</li>
 *   <li>✅ Separation ensures clean architecture</li>
 * </ul>
 * 
 * <p><b>Global Support:</b></p>
 * <ul>
 *   <li>✅ Title (Mr, Miss, Mrs, Ms, Dr, Prof, etc.)</li>
 *   <li>✅ Gender (MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY)</li>
 *   <li>✅ Birth date for age calculations</li>
 *   <li>✅ Emergency contact</li>
 *   <li>✅ Nationality (ISO country code)</li>
 * </ul>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * Employee employee = Employee.builder()
 *     .userId(user.getId())
 *     .title(Title.MR)
 *     .gender(Gender.MALE)
 *     .birthDate(LocalDate.of(1990, 1, 1))
 *     .nationality("TR")
 *     .build();
 * }</pre>
 */
@Entity
@Table(name = "human_employee", schema = "human",
    indexes = {
        @Index(name = "idx_employee_user", columnList = "user_id", unique = true),
        @Index(name = "idx_employee_tenant", columnList = "tenant_id"),
        @Index(name = "idx_employee_employee_number", columnList = "tenant_id,employee_number", unique = true)
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee extends BaseEntity {

    /**
     * One-to-One relationship with User entity.
     * <p>Each User can have one Employee record (optional).</p>
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * Personal title/salutation (Mr, Miss, Mrs, Ms, Dr, Prof, etc.).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "title", length = 20)
    private Title title;

    /**
     * Gender identity.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    /**
     * Birth date for age calculations and HR requirements.
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Nationality (ISO 3166-1 alpha-2 country code).
     * <p>Examples: "TR", "US", "GB", "DE"</p>
     */
    @Column(name = "nationality", length = 2)
    private String nationality;

    /**
     * Employee number (company-specific unique identifier).
     * <p>Format: Company-specific (e.g., "EMP-001", "2024-001")</p>
     */
    @Column(name = "employee_number", length = 50)
    private String employeeNumber;

    /**
     * Hire date (employment start date).
     */
    @Column(name = "hire_date")
    private LocalDate hireDate;

    /**
     * Termination date (if applicable).
     */
    @Column(name = "termination_date")
    private LocalDate terminationDate;

    /**
     * Emergency contact information.
     */
    @Embedded
    private EmergencyContact emergencyContact;

    /**
     * HR Compliance Status - Tracks completeness of employee HR record.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "hr_compliance_status", length = 30)
    private HrComplianceStatus hrComplianceStatus;

    /**
     * Missing recommended HR fields (stored as comma-separated list).
     * <p>Examples: "employeeNumber,hireDate" or "department,emergencyContact"</p>
     */
    @Column(name = "missing_fields", length = 500)
    private String missingFields;

    /**
     * Last compliance check timestamp.
     */
    @Column(name = "last_compliance_check_at")
    private Instant lastComplianceCheckAt;

    /**
     * Calculate age from birth date.
     * 
     * @return Age in years, or null if birth date not set
     */
    public Integer calculateAge() {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * Check if employee is currently active (not terminated).
     * 
     * @return true if active
     */
    public boolean isActive() {
        return terminationDate == null;
    }

    /**
     * Get full display name with title.
     * 
     * <p>Format: "Mr. John Doe" or "Dr. Jane Smith"</p>
     * 
     * @param firstName User's first name
     * @param lastName User's last name
     * @return Formatted display name with title
     */
    public String getFormattedDisplayName(String firstName, String lastName) {
        if (title == null || title == Title.NONE) {
            return firstName + " " + lastName;
        }
        
        String titlePrefix = switch (title) {
            case MR -> "Mr.";
            case MISS -> "Miss";
            case MRS -> "Mrs.";
            case MS -> "Ms.";
            case DR -> "Dr.";
            case PROF -> "Prof.";
            case ENG -> "Eng.";
            case NONE -> "";
        };
        
        return titlePrefix + " " + firstName + " " + lastName;
    }

    /**
     * Check HR compliance and update status.
     * 
     * <p>Recommended fields: employeeNumber, hireDate, department (from User), emergencyContact</p>
     * 
     * @param department Department name from User entity (optional, can be null)
     * @return List of missing recommended field names
     */
    public List<String> checkComplianceAndUpdateStatus(String department) {
        List<String> missingFieldsList = new java.util.ArrayList<>();

        if (employeeNumber == null || employeeNumber.isBlank()) {
            missingFieldsList.add("employeeNumber");
        }
        if (hireDate == null) {
            missingFieldsList.add("hireDate");
        }
        if (department == null || department.isBlank()) {
            missingFieldsList.add("department");
        }
        if (emergencyContact == null) {
            missingFieldsList.add("emergencyContact");
        }

        this.missingFields = missingFieldsList.isEmpty() ? null : String.join(",", missingFieldsList);
        this.lastComplianceCheckAt = Instant.now();
        
        if (missingFieldsList.isEmpty()) {
            this.hrComplianceStatus = HrComplianceStatus.COMPLETE;
        } else {
            this.hrComplianceStatus = HrComplianceStatus.MISSING_RECOMMENDED;
        }

        return missingFieldsList;
    }

    /**
     * Check if HR record is compliance-complete.
     */
    public boolean isComplianceComplete() {
        return hrComplianceStatus == HrComplianceStatus.COMPLETE;
    }

    @Override
    protected String getModuleCode() {
        return "EMP";
    }
}


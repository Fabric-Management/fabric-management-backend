package com.fabricmanagement.human.employee.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.employee.domain.Employee;
import com.fabricmanagement.human.employee.domain.EmergencyContact;
import com.fabricmanagement.human.employee.domain.Gender;
import com.fabricmanagement.human.employee.domain.Title;
import com.fabricmanagement.human.employee.infra.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Employee Service - HR/İK data management.
 * 
 * <p><b>CRITICAL SEPARATION:</b></p>
 * <ul>
 *   <li>User = Authentication, basic identity, platform access</li>
 *   <li>Employee = HR data, personal info, employment details</li>
 * </ul>
 * 
 * <p><b>One-to-One Relationship:</b> Each User can have one Employee record (optional).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final com.fabricmanagement.human.employee.infra.repository.EmployeeNumberSequenceRepository sequenceRepository;

    /**
     * Create or update employee record for user.
     * 
     * @param userId User ID (One-to-One relationship)
     * @param title Personal title
     * @param gender Gender identity
     * @param birthDate Birth date
     * @param nationality ISO country code
     * @param employeeNumber Company-specific employee number
     * @param hireDate Employment start date
     * @param emergencyContact Emergency contact information
     * @return Created or updated employee
     */
    @Transactional
    public Employee createOrUpdateEmployee(
            UUID userId,
            Title title,
            Gender gender,
            LocalDate birthDate,
            String nationality,
            String employeeNumber,
            LocalDate hireDate,
            EmergencyContact emergencyContact) {
        
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        log.debug("Creating/updating employee: userId={}, tenantId={}", userId, tenantId);

        Employee employee = employeeRepository.findByUserId(userId)
            .orElseGet(() -> {
                Employee newEmployee = Employee.builder()
                    .userId(userId)
                    .build();
                newEmployee.setTenantId(tenantId);
                return newEmployee;
            });

        employee.setTitle(title);
        employee.setGender(gender);
        employee.setBirthDate(birthDate);
        employee.setNationality(nationality);
        employee.setEmployeeNumber(employeeNumber);
        employee.setHireDate(hireDate);
        employee.setEmergencyContact(emergencyContact);

        return employeeRepository.save(employee);
    }

    /**
     * Check HR compliance and update employee record.
     * 
     * @param employee Employee entity to check
     * @param department Department name from User entity (optional)
     * @return List of missing recommended field names
     */
    @Transactional
    public List<String> checkAndUpdateCompliance(Employee employee, String department) {
        if (employee == null) {
            return List.of();
        }
        
        List<String> missingFields = employee.checkComplianceAndUpdateStatus(department);
        employeeRepository.save(employee);
        
        if (!missingFields.isEmpty()) {
            log.warn("⚠️ HR Compliance: Employee userId={} missing recommended fields: {}", 
                employee.getUserId(), String.join(", ", missingFields));
        } else {
            log.debug("✅ HR Compliance: Employee userId={} is complete", employee.getUserId());
        }
        
        return missingFields;
    }

    /**
     * Get employee by user ID.
     */
    public Optional<Employee> getEmployeeByUserId(UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return employeeRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    /**
     * Get employee by employee number.
     */
    public Optional<Employee> getEmployeeByEmployeeNumber(String employeeNumber) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return employeeRepository.findByTenantIdAndEmployeeNumber(tenantId, employeeNumber);
    }

    /**
     * Check if employee exists for user.
     */
    public boolean existsByUserId(UUID userId) {
        return employeeRepository.existsByUserId(userId);
    }

    /**
     * Generate employee number in format: {TENANT_UID}-EMP-{SEQUENCE}
     * 
     * <p>Example: "ACME-001-EMP-00042"</p>
     * 
     * <p>Format breakdown:
     * <ul>
     *   <li>{TENANT_UID}: Company tenant UID (e.g., "ACME-001")</li>
     *   <li>EMP: Employee module code</li>
     *   <li>{SEQUENCE}: Auto-incrementing global sequence (e.g., "00042")</li>
     * </ul>
     * 
     * <p><b>Design Decision - Global Sequence (Not Year-Based):</b>
     * <ul>
     *   <li>✅ No sequence reset at year boundary</li>
     *   <li>✅ Unique sequence numbers (never duplicates)</li>
     *   <li>✅ No sequence exhaustion (supports unlimited employees)</li>
     *   <li>✅ Year information available from Employee.hireDate if needed</li>
     * </ul>
     * 
     * <p><b>Optimization:</b> Uses dedicated sequence table with pessimistic locking
     * instead of scanning all employees. This ensures:
     * <ul>
     *   <li>✅ Single query (no full table scan)</li>
     *   <li>✅ Atomic sequence increment (no race conditions)</li>
     *   <li>✅ Database-level locking (SELECT FOR UPDATE)</li>
     *   <li>✅ Efficient for high-concurrency scenarios</li>
     * </ul>
     * 
     * @return Generated employee number
     */
    @Transactional
    public String generateEmployeeNumber() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String tenantUid = TenantContext.getCurrentTenantUid();
        
        if (tenantUid == null) {
            tenantUid = "SYS-000";
        }
        
        // Get or create sequence with pessimistic lock (SELECT FOR UPDATE)
        com.fabricmanagement.human.employee.domain.EmployeeNumberSequence sequence = 
            sequenceRepository.findByTenantIdForUpdate(tenantId)
                .orElseGet(() -> {
                    // Create new sequence if doesn't exist (inside transaction)
                    // Note: If another thread creates it simultaneously, we'll get unique constraint violation
                    // In that case, retry by finding the existing one
                    try {
                        com.fabricmanagement.human.employee.domain.EmployeeNumberSequence newSeq = 
                            com.fabricmanagement.human.employee.domain.EmployeeNumberSequence.create(tenantId);
                        sequenceRepository.saveAndFlush(newSeq);
                        // Re-acquire lock after save to ensure we have the latest version
                        return sequenceRepository.findByTenantIdForUpdate(tenantId)
                            .orElse(newSeq);
                    } catch (Exception e) {
                        // If unique constraint violation (another thread created it), find it
                        log.debug("Sequence already exists, fetching: tenantId={}", tenantId);
                        return sequenceRepository.findByTenantIdForUpdate(tenantId)
                            .orElseThrow(() -> new IllegalStateException("Failed to create or find sequence"));
                    }
                });
        
        // Atomically increment and get next sequence
        Integer nextSequence = sequence.getNextAndIncrement();
        sequenceRepository.save(sequence);
        
        // Format: {TENANT_UID}-EMP-{SEQUENCE}
        String employeeNumber = String.format("%s-EMP-%05d", 
            tenantUid, nextSequence);
        
        log.debug("Generated employee number: {}", employeeNumber);
        return employeeNumber;
    }
}


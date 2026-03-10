package com.fabricmanagement.human.core.employee.application;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.app.EmployeeComplianceContext;
import com.fabricmanagement.human.compliance.app.EmployeeCompliancePolicyRegistry;
import com.fabricmanagement.human.compliance.domain.EmployeeCompliancePolicy;
import com.fabricmanagement.human.core.employee.domain.EmergencyContact;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.human.core.employee.domain.EmployeeNumberSequence;
import com.fabricmanagement.human.core.employee.domain.Gender;
import com.fabricmanagement.human.core.employee.domain.Title;
import com.fabricmanagement.human.core.employee.domain.event.EmployeeUpdatedEvent;
import com.fabricmanagement.human.core.employee.infra.repository.EmployeeNumberSequenceRepository;
import com.fabricmanagement.human.core.employee.infra.repository.EmployeeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

  private final EmployeeRepository employeeRepository;
  private final EmployeeNumberSequenceRepository sequenceRepository;
  private final EmployeeCompliancePolicyRegistry compliancePolicyRegistry;
  private final DomainEventPublisher eventPublisher;

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

    Employee employee =
        employeeRepository
            .findByUserId(userId)
            .orElseGet(
                () -> {
                  Employee newEmployee = Employee.builder().userId(userId).build();
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

    Employee saved = employeeRepository.save(employee);
    eventPublisher.publish(new EmployeeUpdatedEvent(tenantId, userId));
    return saved;
  }

  @Transactional
  public List<String> checkAndUpdateCompliance(Employee employee, String department) {
    if (employee == null) {
      return List.of();
    }

    EmployeeCompliancePolicy policy = compliancePolicyRegistry.resolve();
    List<String> missingFields =
        policy.evaluate(employee, EmployeeComplianceContext.of(department));
    employee.applyComplianceResult(missingFields);
    employeeRepository.save(employee);

    if (!missingFields.isEmpty()) {
      log.warn(
          "⚠️ HR Compliance: Employee userId={} missing recommended fields: {}",
          employee.getUserId(),
          String.join(", ", missingFields));
    } else {
      log.debug("✅ HR Compliance: Employee userId={} is complete", employee.getUserId());
    }

    return missingFields;
  }

  @Transactional
  public Employee saveEmployee(Employee employee) {
    Employee saved = employeeRepository.save(employee);
    eventPublisher.publish(new EmployeeUpdatedEvent(employee.getTenantId(), employee.getUserId()));
    return saved;
  }

  public Optional<Employee> getEmployeeByUserId(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return employeeRepository.findByTenantIdAndUserId(tenantId, userId);
  }

  /**
   * Tenant-explicit overload for contexts where TenantContext may not be set (e.g. platform admin,
   * password setup across tenants).
   */
  public Optional<Employee> getEmployeeByUserId(UUID tenantId, UUID userId) {
    return employeeRepository.findByTenantIdAndUserId(tenantId, userId);
  }

  /**
   * Batch-fetch employees by user IDs. Returns a map of userId -> Employee for efficient lookup
   * when enriching user lists with employee data.
   */
  @Transactional(readOnly = true)
  public java.util.Map<UUID, Employee> getEmployeesByUserIds(
      UUID tenantId, java.util.Collection<UUID> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return java.util.Map.of();
    }
    return employeeRepository.findByTenantIdAndUserIdIn(tenantId, userIds).stream()
        .collect(java.util.stream.Collectors.toMap(Employee::getUserId, e -> e));
  }

  public Optional<Employee> getEmployeeByEmployeeNumber(String employeeNumber) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return employeeRepository.findByTenantIdAndEmployeeNumber(tenantId, employeeNumber);
  }

  public boolean existsByUserId(UUID userId) {
    return employeeRepository.existsByUserId(userId);
  }

  @Transactional
  public String generateEmployeeNumber() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    String tenantUid = TenantContext.getCurrentTenantUid();

    if (tenantUid == null) {
      tenantUid = "SYS-000";
    }

    EmployeeNumberSequence sequence =
        sequenceRepository
            .findByTenantIdForUpdate(tenantId)
            .orElseGet(
                () -> {
                  try {
                    EmployeeNumberSequence newSeq = EmployeeNumberSequence.create(tenantId);
                    sequenceRepository.saveAndFlush(newSeq);
                    return sequenceRepository.findByTenantIdForUpdate(tenantId).orElse(newSeq);
                  } catch (Exception e) {
                    log.debug("Sequence already exists, fetching: tenantId={}", tenantId);
                    return sequenceRepository
                        .findByTenantIdForUpdate(tenantId)
                        .orElseThrow(
                            () -> new IllegalStateException("Failed to create or find sequence"));
                  }
                });

    Integer nextSequence = sequence.getNextAndIncrement();
    sequenceRepository.save(sequence);

    String employeeNumber = String.format("%s-EMP-%05d", tenantUid, nextSequence);

    log.debug("Generated employee number: {}", employeeNumber);
    return employeeNumber;
  }

  @Transactional
  public Employee terminateEmployee(UUID userId, LocalDate terminationDate) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Terminating employee. userId={}, terminationDate={}", userId, terminationDate);

    Employee employee =
        getEmployeeByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

    employee.terminate(terminationDate);
    Employee saved = employeeRepository.save(employee);

    eventPublisher.publish(
        new com.fabricmanagement.human.core.employee.domain.event.EmployeeTerminatedEvent(
            tenantId, userId, terminationDate));
    return saved;
  }
}

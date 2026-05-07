package com.fabricmanagement.human.core.employee.app.adapter;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.human.core.employee.app.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.EmergencyContact;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.platform.user.domain.EmployeeFieldUpdates;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.port.EmployeeCompliancePort;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationCommand;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationPort;
import com.fabricmanagement.platform.user.domain.port.EmployeeMutationPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EmployeeCreationAdapter
    implements EmployeeCreationPort, EmployeeMutationPort, EmployeeCompliancePort {

  private final EmployeeService employeeService;

  @Override
  @Transactional
  public EmployeeSnapshot createOrUpdate(EmployeeCreationCommand command) {
    Employee employee = employeeService.createOrUpdateEmployee(command);
    return EmployeeSnapshotFactory.fromEntity(employee);
  }

  @Override
  public String generateEmployeeNumber() {
    return employeeService.generateEmployeeNumber();
  }

  @Override
  @Transactional
  public Optional<EmployeeSnapshot> applyFieldUpdates(UUID userId, EmployeeFieldUpdates updates) {
    return employeeService
        .getEmployeeByUserId(userId)
        .map(
            employee -> {
              if (updates.title() != null) {
                employee.setTitle(updates.title());
              }
              if (updates.gender() != null) {
                employee.setGender(updates.gender());
              }
              if (updates.birthDate() != null) {
                employee.setBirthDate(updates.birthDate());
              }
              if (updates.nationality() != null) {
                employee.setNationality(updates.nationality());
              }
              if (updates.employeeNumber() != null) {
                employee.setEmployeeNumber(updates.employeeNumber());
              }
              if (updates.hireDate() != null) {
                employee.setHireDate(updates.hireDate());
              }
              if (updates.emergencyContact() != null) {
                employee.setEmergencyContact(toDomainEmergencyContact(updates.emergencyContact()));
              }
              Employee saved = employeeService.saveEmployee(employee);
              return EmployeeSnapshotFactory.fromEntity(saved);
            });
  }

  @Override
  @Transactional
  public List<String> runComplianceEvaluation(UUID userId, String departmentName) {
    return employeeService
        .getEmployeeByUserId(userId)
        .map(employee -> employeeService.checkAndUpdateCompliance(employee, departmentName))
        .orElse(List.of());
  }

  private static EmergencyContact toDomainEmergencyContact(EmergencyContactData data) {
    if (data == null || data.isEmpty()) {
      return null;
    }
    return EmergencyContact.builder()
        .name(data.name())
        .phone(data.phone())
        .relationship(data.relationship())
        .build();
  }
}

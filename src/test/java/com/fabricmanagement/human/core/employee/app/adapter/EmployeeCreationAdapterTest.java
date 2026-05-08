package com.fabricmanagement.human.core.employee.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.human.core.employee.app.EmployeeService;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.platform.user.domain.EmployeeFieldUpdates;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.port.EmployeeCreationCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmployeeCreationAdapter")
class EmployeeCreationAdapterTest {

  @Mock private EmployeeService employeeService;
  @InjectMocks private EmployeeCreationAdapter adapter;

  @Test
  @DisplayName("createOrUpdate delegates to EmployeeService with domain mapping")
  void createOrUpdate_Delegation() {
    UUID userId = UUID.randomUUID();
    EmergencyContactData ecData = new EmergencyContactData("John", "123", "Brother");
    Employee employee = Employee.builder().userId(userId).employeeNumber("EMP-123").build();

    EmployeeCreationCommand command =
        new EmployeeCreationCommand(
            userId, Title.MR, Gender.MALE, null, null, "EMP-123", null, ecData, null);

    when(employeeService.createOrUpdateEmployee(any(EmployeeCreationCommand.class)))
        .thenReturn(employee);

    EmployeeSnapshot result = adapter.createOrUpdate(command);

    assertThat(result.isPresent()).isTrue();
    assertThat(result.employeeNumber()).isEqualTo("EMP-123");
    verify(employeeService).createOrUpdateEmployee(command);
  }

  @Test
  @DisplayName("generateEmployeeNumber delegates to EmployeeService")
  void generateEmployeeNumber_Delegation() {
    when(employeeService.generateEmployeeNumber()).thenReturn("EMP-999");
    assertThat(adapter.generateEmployeeNumber()).isEqualTo("EMP-999");
  }

  @Test
  @DisplayName("applyFieldUpdates applies fields to entity and saves")
  void applyFieldUpdates_Success() {
    UUID userId = UUID.randomUUID();
    Employee employee = Employee.builder().userId(userId).title(Title.MR).build();
    EmployeeFieldUpdates updates =
        new EmployeeFieldUpdates(Title.DR, Gender.MALE, null, "TR", "NEW-NUM", null, null);

    when(employeeService.getEmployeeByUserId(userId)).thenReturn(Optional.of(employee));
    when(employeeService.saveEmployee(any(Employee.class))).thenReturn(employee);

    Optional<EmployeeSnapshot> result = adapter.applyFieldUpdates(userId, updates);

    assertThat(result).isPresent();
    assertThat(employee.getTitle()).isEqualTo(Title.DR);
    assertThat(employee.getGender()).isEqualTo(Gender.MALE);
    assertThat(employee.getNationality()).isEqualTo("TR");
    assertThat(employee.getEmployeeNumber()).isEqualTo("NEW-NUM");
    verify(employeeService).saveEmployee(employee);
  }

  @Test
  @DisplayName("runComplianceEvaluation delegates to Service Check")
  void runCompliance_Success() {
    UUID userId = UUID.randomUUID();
    Employee employee = Employee.builder().userId(userId).build();
    when(employeeService.getEmployeeByUserId(userId)).thenReturn(Optional.of(employee));
    when(employeeService.checkAndUpdateCompliance(employee, "HR")).thenReturn(List.of("field1"));

    List<String> missing = adapter.runComplianceEvaluation(userId, "HR");

    assertThat(missing).containsExactly("field1");
    verify(employeeService).checkAndUpdateCompliance(employee, "HR");
  }
}

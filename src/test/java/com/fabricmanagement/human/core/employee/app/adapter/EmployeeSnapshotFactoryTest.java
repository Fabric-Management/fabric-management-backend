package com.fabricmanagement.human.core.employee.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.human.core.employee.domain.EmergencyContact;
import com.fabricmanagement.human.core.employee.domain.Employee;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EmployeeSnapshotFactory")
class EmployeeSnapshotFactoryTest {

  @Test
  @DisplayName("returns absent when input is null")
  void returnsAbsentWhenNull() {
    assertThat(EmployeeSnapshotFactory.fromEntity(null).isPresent()).isFalse();
  }

  @Test
  @DisplayName("returns absent when userId is null")
  void returnsAbsentWhenUserIdIsNull() {
    Employee employee = new Employee();
    assertThat(EmployeeSnapshotFactory.fromEntity(employee).isPresent()).isFalse();
  }

  @Nested
  @DisplayName("Mapping")
  class Mapping {

    @Test
    @DisplayName("maps all basic fields correctly")
    void mapsBasicFields() {
      UUID userId = UUID.randomUUID();
      LocalDate birthDate = LocalDate.of(1990, 1, 1);
      LocalDate hireDate = LocalDate.of(2020, 5, 10);

      Employee employee =
          Employee.builder()
              .userId(userId)
              .title(Title.MR)
              .gender(Gender.MALE)
              .birthDate(birthDate)
              .nationality("TR")
              .employeeNumber("EMP-001")
              .hireDate(hireDate)
              .build();

      EmployeeSnapshot snapshot = EmployeeSnapshotFactory.fromEntity(employee);

      assertThat(snapshot.isPresent()).isTrue();
      assertThat(snapshot.userId()).isEqualTo(userId);
      assertThat(snapshot.title()).isEqualTo(Title.MR);
      assertThat(snapshot.gender()).isEqualTo(Gender.MALE);
      assertThat(snapshot.birthDate()).isEqualTo(birthDate);
      assertThat(snapshot.nationality()).isEqualTo("TR");
      assertThat(snapshot.employeeNumber()).isEqualTo("EMP-001");
      assertThat(snapshot.hireDate()).isEqualTo(hireDate);
      assertThat(snapshot.emergencyContact()).isNull();
    }

    @Test
    @DisplayName("maps emergency contact correctly when available")
    void mapsEmergencyContact() {
      EmergencyContact ec = new EmergencyContact("John Doe", "123456789", "Brother");
      Employee employee = Employee.builder().userId(UUID.randomUUID()).emergencyContact(ec).build();

      EmployeeSnapshot snapshot = EmployeeSnapshotFactory.fromEntity(employee);

      assertThat(snapshot.emergencyContact()).isNotNull();
      assertThat(snapshot.emergencyContact().name()).isEqualTo("John Doe");
      assertThat(snapshot.emergencyContact().phone()).isEqualTo("123456789");
      assertThat(snapshot.emergencyContact().relationship()).isEqualTo("Brother");
    }

    @Test
    @DisplayName("returns null emergency contact when it is empty")
    void mapsEmptyEmergencyContactAsNull() {
      EmergencyContact ec = new EmergencyContact("", " ", null);
      Employee employee = Employee.builder().userId(UUID.randomUUID()).emergencyContact(ec).build();

      EmployeeSnapshot snapshot = EmployeeSnapshotFactory.fromEntity(employee);

      assertThat(snapshot.emergencyContact()).isNull();
    }

    @Test
    @DisplayName("maps partial emergency contact when one field is filled")
    void mapsPartialEmergencyContact() {
      EmergencyContact ec = new EmergencyContact("Only Name", null, null);
      Employee employee = Employee.builder().userId(UUID.randomUUID()).emergencyContact(ec).build();

      EmployeeSnapshot snapshot = EmployeeSnapshotFactory.fromEntity(employee);

      assertThat(snapshot.emergencyContact()).isNotNull();
      assertThat(snapshot.emergencyContact().name()).isEqualTo("Only Name");
      assertThat(snapshot.emergencyContact().phone()).isNull();
      assertThat(snapshot.emergencyContact().relationship()).isNull();
    }
  }
}

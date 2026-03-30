package com.fabricmanagement.platform.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.identity.EmergencyContactData;
import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.User;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserDto Mapping")
class UserDtoTest {

  private final UUID userId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();

  @Test
  @DisplayName("maps basic User fields without HR data")
  void mapsUserOnly() {
    User user = User.builder().firstName("John").lastName("Doe").organizationId(orgId).build();
    user.setIsActive(true);
    user.setId(userId);

    UserDto dto = UserDto.from(user);

    assertThat(dto.getFirstName()).isEqualTo("John");
    assertThat(dto.getLastName()).isEqualTo("Doe");
    assertThat(dto.getIsEmployee()).isFalse();
    assertThat(dto.getEmployeeNumber()).isNull();
  }

  @Test
  @DisplayName("maps User with full HR snapshot")
  void mapsUserWithSnapshot() {
    User user = User.builder().firstName("Jane").lastName("Doe").organizationId(orgId).build();
    user.setId(userId);

    EmployeeSnapshot snapshot =
        new EmployeeSnapshot(
            userId,
            Title.MS,
            Gender.FEMALE,
            LocalDate.of(1995, 5, 5),
            "US",
            "E-100",
            LocalDate.of(2021, 1, 1),
            new EmergencyContactData("Mom", "555-1212", "Parent"));

    UserDto dto = UserDto.from(user, snapshot);

    assertThat(dto.getIsEmployee()).isTrue();
    assertThat(dto.getTitle()).isEqualTo("MS");
    assertThat(dto.getGender()).isEqualTo("FEMALE");
    assertThat(dto.getEmployeeNumber()).isEqualTo("E-100");
    assertThat(dto.getEmergencyContact()).isNotNull();
    assertThat(dto.getEmergencyContact().getName()).isEqualTo("Mom");
  }

  @Test
  @DisplayName("treats absent snapshot as not an employee")
  void handlesAbsentSnapshot() {
    User user = User.builder().firstName("Jane").lastName("Doe").organizationId(orgId).build();
    user.setId(userId);

    UserDto dto = UserDto.from(user, EmployeeSnapshot.absent());

    assertThat(dto.getIsEmployee()).isFalse();
    assertThat(dto.getEmployeeNumber()).isNull();
  }

  @Test
  @DisplayName("handles snapshot with null emergency contact")
  void handlesNullEmergencyContact() {
    User user = User.builder().firstName("Jane").lastName("Doe").organizationId(orgId).build();
    user.setId(userId);

    EmployeeSnapshot snapshot =
        new EmployeeSnapshot(userId, null, null, null, null, "E-101", null, null);

    UserDto dto = UserDto.from(user, snapshot);

    assertThat(dto.getIsEmployee()).isTrue();
    assertThat(dto.getEmployeeNumber()).isEqualTo("E-101");
    assertThat(dto.getEmergencyContact()).isNull();
  }
}

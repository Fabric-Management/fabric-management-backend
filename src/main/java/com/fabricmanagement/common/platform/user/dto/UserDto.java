package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.UserDepartment;
import com.fabricmanagement.human.core.employee.domain.Employee;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

  private UUID id;
  private UUID tenantId;
  private String uid;
  private String firstName;
  private String lastName;
  private String displayName;
  private UUID organizationId;
  private UUID roleId;
  private String role; // Role name for display
  private UUID departmentId; // Primary department ID
  private String departmentName; // Primary department name for display
  private String userType;
  private Boolean isActive;
  private Instant lastActiveAt;
  private Instant onboardingCompletedAt;
  private Boolean hasCompletedOnboarding;
  private Instant createdAt;
  private Instant updatedAt;

  // Employee (HR) fields — populated when user has an Employee record
  private Boolean isEmployee;
  private String title;
  private String gender;
  private LocalDate birthDate;
  private String nationality;
  private String employeeNumber;
  private LocalDate hireDate;
  private EmergencyContactDto emergencyContact;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EmergencyContactDto {
    private String name;
    private String phone;
    private String relationship;
  }

  public static UserDto from(User user) {
    return from(user, null);
  }

  public static UserDto from(User user, Employee employee) {
    UserDtoBuilder builder =
        UserDto.builder()
            .id(user.getId())
            .tenantId(user.getTenantId())
            .uid(user.getUid())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .displayName(user.getDisplayName())
            .organizationId(user.getOrganizationId())
            .roleId(user.getRole() != null ? user.getRole().getId() : null)
            .role(user.getRole() != null ? user.getRole().getRoleName() : null)
            .userType(user.getUserType() != null ? user.getUserType().name() : null)
            .departmentId(
                user.getUserDepartments().stream()
                    .filter(
                        ud ->
                            Boolean.TRUE.equals(ud.getIsPrimary())
                                && Boolean.TRUE.equals(ud.getIsActive()))
                    .findFirst()
                    .map(UserDepartment::getDepartmentId)
                    .orElse(
                        user.getUserDepartments().stream()
                            .filter(ud -> Boolean.TRUE.equals(ud.getIsActive()))
                            .findFirst()
                            .map(UserDepartment::getDepartmentId)
                            .orElse(null)))
            .departmentName(
                user.getUserDepartments().stream()
                    .filter(
                        ud ->
                            Boolean.TRUE.equals(ud.getIsPrimary())
                                && Boolean.TRUE.equals(ud.getIsActive()))
                    .findFirst()
                    .map(
                        ud ->
                            ud.getDepartment() != null
                                ? ud.getDepartment().getDepartmentName()
                                : null)
                    .orElse(
                        user.getUserDepartments().stream()
                            .filter(ud -> Boolean.TRUE.equals(ud.getIsActive()))
                            .findFirst()
                            .map(
                                ud ->
                                    ud.getDepartment() != null
                                        ? ud.getDepartment().getDepartmentName()
                                        : null)
                            .orElse(null)))
            .isActive(user.getIsActive())
            .lastActiveAt(user.getLastActiveAt())
            .onboardingCompletedAt(user.getOnboardingCompletedAt())
            .hasCompletedOnboarding(user.hasCompletedOnboarding())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt());

    if (employee != null) {
      builder
          .isEmployee(true)
          .title(employee.getTitle() != null ? employee.getTitle().name() : null)
          .gender(employee.getGender() != null ? employee.getGender().name() : null)
          .birthDate(employee.getBirthDate())
          .nationality(employee.getNationality())
          .employeeNumber(employee.getEmployeeNumber())
          .hireDate(employee.getHireDate());

      if (employee.getEmergencyContact() != null && !employee.getEmergencyContact().isEmpty()) {
        builder.emergencyContact(
            EmergencyContactDto.builder()
                .name(employee.getEmergencyContact().getName())
                .phone(employee.getEmergencyContact().getPhone())
                .relationship(employee.getEmergencyContact().getRelationship())
                .build());
      }
    } else {
      builder.isEmployee(false);
    }

    return builder.build();
  }
}

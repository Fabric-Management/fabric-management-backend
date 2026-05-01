package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.platform.user.domain.EmployeeSnapshot;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
  private String roleCode;
  private UUID departmentId; // Primary department ID
  private String departmentName; // Primary department name for display
  private List<String> departmentCodes;
  private String primaryDepartmentCode;
  private String userType;
  private Boolean isActive;
  private Integer wipLimit;
  private Instant lastActiveAt;
  private Instant onboardingCompletedAt;
  private Boolean hasCompletedOnboarding;
  private Instant createdAt;
  private Instant updatedAt;

  private java.util.Map<
          String, java.util.Map<String, com.fabricmanagement.platform.user.domain.DataScope>>
      permissions;
  private boolean superAdmin;

  /** User-level locale preference (e.g. "tr-TR"). Null = inherits from tenant settings. */
  private String preferredLocale;

  /** User-level timezone preference (e.g. "Europe/Istanbul"). Null = inherits from tenant. */
  private String preferredTimezone;

  // Work location label — populated from user's primary work location address
  private String workLocationLabel;

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

  public static UserDto from(User user, EmployeeSnapshot employee) {
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
            .roleCode(user.getRole() != null ? user.getRole().getRoleCode() : null)
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
            .departmentCodes(
                user.getUserDepartments().stream()
                    .filter(ud -> Boolean.TRUE.equals(ud.getIsActive()))
                    .map(
                        ud ->
                            ud.getDepartment() != null
                                ? ud.getDepartment().getDepartmentCode()
                                : null)
                    .filter(Objects::nonNull)
                    .toList())
            .primaryDepartmentCode(
                user.getUserDepartments().stream()
                    .filter(
                        ud ->
                            Boolean.TRUE.equals(ud.getIsPrimary())
                                && Boolean.TRUE.equals(ud.getIsActive()))
                    .findFirst()
                    .map(
                        ud ->
                            ud.getDepartment() != null
                                ? ud.getDepartment().getDepartmentCode()
                                : null)
                    .orElse(null))
            .isActive(user.getIsActive())
            .wipLimit(user.getWipLimit())
            .lastActiveAt(user.getLastActiveAt())
            .onboardingCompletedAt(user.getOnboardingCompletedAt())
            .hasCompletedOnboarding(user.hasCompletedOnboarding())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .preferredLocale(user.getPreferredLocale())
            .preferredTimezone(user.getPreferredTimezone());

    if (employee != null && employee.isPresent()) {
      builder
          .isEmployee(true)
          .title(employee.title() != null ? employee.title().name() : null)
          .gender(employee.gender() != null ? employee.gender().name() : null)
          .birthDate(employee.birthDate())
          .nationality(employee.nationality())
          .employeeNumber(employee.employeeNumber())
          .hireDate(employee.hireDate());

      if (employee.emergencyContact() != null && !employee.emergencyContact().isEmpty()) {
        builder.emergencyContact(
            EmergencyContactDto.builder()
                .name(employee.emergencyContact().name())
                .phone(employee.emergencyContact().phone())
                .relationship(employee.emergencyContact().relationship())
                .build());
      }
    } else {
      builder.isEmployee(false);
    }

    return builder.build();
  }
}

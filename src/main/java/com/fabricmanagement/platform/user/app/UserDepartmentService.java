package com.fabricmanagement.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User Department Service - Business logic for user-department assignments.
 *
 * <p>Handles Many-to-Many relationship between User and Department.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Assign users to departments
 *   <li>Remove user-department assignments
 *   <li>Manage primary department designation
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDepartmentService {

  private final UserRepository userRepository;
  private final DepartmentRepository departmentRepository;
  private final UserDepartmentRepository userDepartmentRepository;

  @Transactional(readOnly = true)
  public List<UserDepartment> getUserDepartments(UUID userId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Finding user departments: tenantId={}, userId={}", tenantId, userId);

    return userDepartmentRepository.findByTenantIdAndUserId(tenantId, userId);
  }

  @Transactional(readOnly = true)
  public Optional<UserDepartment> getPrimaryDepartment(UUID userId) {
    log.debug("Finding primary department: userId={}", userId);

    return userDepartmentRepository.findByUserIdAndIsPrimaryTrue(userId);
  }

  @Transactional
  public UserDepartment assignDepartment(
      UUID userId, UUID departmentId, boolean isPrimary, UUID assignedBy) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Assigning department to user: tenantId={}, userId={}, departmentId={}, isPrimary={}",
        tenantId,
        userId,
        departmentId,
        isPrimary);

    User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "USER_NOT_FOUND", 404));

    Department department =
        departmentRepository
            .findByTenantIdAndId(tenantId, departmentId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Department not found", "USER_DEPT_NOT_FOUND", 404));

    if (userDepartmentRepository.findByUserIdAndDepartmentId(userId, departmentId).isPresent()) {
      throw new PlatformDomainException(
          "User is already assigned to this department", "USER_DEPT_ALREADY_ASSIGNED", 409);
    }

    if (isPrimary) {
      userDepartmentRepository
          .findByUserIdAndIsPrimaryTrue(userId)
          .ifPresent(existing -> existing.markAsSecondary());
    }

    UserDepartment assignment = UserDepartment.create(user, department, isPrimary, assignedBy);
    UserDepartment saved = userDepartmentRepository.save(assignment);

    log.info(
        "Department assigned: userId={}, departmentId={}, isPrimary={}",
        userId,
        departmentId,
        isPrimary);

    return saved;
  }

  @Transactional
  public void removeAssignment(UUID userId, UUID departmentId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Removing department assignment: tenantId={}, userId={}, departmentId={}",
        tenantId,
        userId,
        departmentId);

    UserDepartment assignment =
        userDepartmentRepository
            .findByUserIdAndDepartmentId(userId, departmentId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Department assignment not found", "USER_DEPT_ASSIGNMENT_NOT_FOUND", 404));

    userDepartmentRepository.delete(assignment);

    log.info("Department assignment removed: userId={}, departmentId={}", userId, departmentId);
  }

  @Transactional
  public void setPrimaryDepartment(UUID userId, UUID departmentId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info(
        "Setting primary department: tenantId={}, userId={}, departmentId={}",
        tenantId,
        userId,
        departmentId);

    UserDepartment assignment =
        userDepartmentRepository
            .findByUserIdAndDepartmentId(userId, departmentId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Department assignment not found", "USER_DEPT_ASSIGNMENT_NOT_FOUND", 404));

    userDepartmentRepository
        .findByUserIdAndIsPrimaryTrue(userId)
        .ifPresent(
            existing -> {
              if (!existing.getDepartmentId().equals(departmentId)) {
                existing.markAsSecondary();
              }
            });

    assignment.markAsPrimary();
    userDepartmentRepository.save(assignment);

    log.info("Primary department set: userId={}, departmentId={}", userId, departmentId);
  }
}

package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.Department;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentRepository;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.UserDepartment;
import com.fabricmanagement.common.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User Department Service - Business logic for user-department assignments.
 *
 * <p>Handles Many-to-Many relationship between User and Department.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Assign users to departments</li>
 *   <li>Remove user-department assignments</li>
 *   <li>Manage primary department designation</li>
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
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Finding user departments: tenantId={}, userId={}", tenantId, userId);

        return userDepartmentRepository.findByTenantIdAndUserId(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public Optional<UserDepartment> getPrimaryDepartment(UUID userId) {
        log.debug("Finding primary department: userId={}", userId);

        return userDepartmentRepository.findByUserIdAndIsPrimaryTrue(userId);
    }

    @Transactional
    public UserDepartment assignDepartment(UUID userId, UUID departmentId, boolean isPrimary, UUID assignedBy) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Assigning department to user: tenantId={}, userId={}, departmentId={}, isPrimary={}", 
            tenantId, userId, departmentId, isPrimary);

        User user = userRepository.findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Department department = departmentRepository.findByTenantIdAndId(tenantId, departmentId)
            .orElseThrow(() -> new IllegalArgumentException("Department not found"));

        if (userDepartmentRepository.findByUserIdAndDepartmentId(userId, departmentId).isPresent()) {
            throw new IllegalArgumentException("User is already assigned to this department");
        }

        if (isPrimary) {
            userDepartmentRepository.findByUserIdAndIsPrimaryTrue(userId)
                .ifPresent(existing -> existing.markAsSecondary());
        }

        UserDepartment assignment = UserDepartment.create(user, department, isPrimary, assignedBy);
        UserDepartment saved = userDepartmentRepository.save(assignment);

        log.info("Department assigned: userId={}, departmentId={}, isPrimary={}", 
            userId, departmentId, isPrimary);

        return saved;
    }

    @Transactional
    public void removeAssignment(UUID userId, UUID departmentId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Removing department assignment: tenantId={}, userId={}, departmentId={}", 
            tenantId, userId, departmentId);

        UserDepartment assignment = userDepartmentRepository
            .findByUserIdAndDepartmentId(userId, departmentId)
            .orElseThrow(() -> new IllegalArgumentException("Department assignment not found"));

        userDepartmentRepository.delete(assignment);

        log.info("Department assignment removed: userId={}, departmentId={}", userId, departmentId);
    }

    @Transactional
    public void setPrimaryDepartment(UUID userId, UUID departmentId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Setting primary department: tenantId={}, userId={}, departmentId={}", 
            tenantId, userId, departmentId);

        UserDepartment assignment = userDepartmentRepository
            .findByUserIdAndDepartmentId(userId, departmentId)
            .orElseThrow(() -> new IllegalArgumentException("Department assignment not found"));

        userDepartmentRepository.findByUserIdAndIsPrimaryTrue(userId)
            .ifPresent(existing -> {
                if (!existing.getDepartmentId().equals(departmentId)) {
                    existing.markAsSecondary();
                }
            });

        assignment.markAsPrimary();
        userDepartmentRepository.save(assignment);

        log.info("Primary department set: userId={}, departmentId={}", userId, departmentId);
    }
}


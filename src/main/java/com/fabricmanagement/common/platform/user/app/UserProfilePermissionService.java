package com.fabricmanagement.common.platform.user.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.domain.UserDepartment;
import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for checking user profile update permissions.
 * 
 * <p>Enforces field-level access control:
 * <ul>
 *   <li>WORK_PROFILE: Admin, HR Manager, or Department Manager (same department)</li>
 *   <li>PERSONAL_PROFILE: Only Admin or HR Manager</li>
 *   <li>Self-update: DENIED for all categories (security)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfilePermissionService {

    private final UserRepository userRepository;

    /**
     * Check if requester can update target user's work profile.
     * 
     * @param requesterId User requesting the update
     * @param targetUserId User whose profile is being updated
     * @return true if allowed, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean canUpdateWorkProfile(UUID requesterId, UUID targetUserId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        // Self-update: DENIED
        if (requesterId.equals(targetUserId)) {
            log.debug("Self-update denied: userId={}", requesterId);
            return false;
        }

        User requester = userRepository.findByTenantIdAndId(tenantId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        
        String roleCode = getRoleCode(requester);
        
        // Admin can update anyone
        if ("ADMIN".equals(roleCode) || "PLATFORM_ADMIN".equals(roleCode)) {
            log.debug("Admin allowed: requesterId={}, targetUserId={}", requesterId, targetUserId);
            return true;
        }
        
        // HR Manager can update anyone
        if ("HR_MANAGER".equals(roleCode) || "HR".equals(roleCode)) {
            log.debug("HR Manager allowed: requesterId={}, targetUserId={}", requesterId, targetUserId);
            return true;
        }
        
        // Department Manager can update users in same department
        if ("DEPT_MANAGER".equals(roleCode) || "MANAGER".equals(roleCode)) {
            boolean sameDepartment = isInSameDepartment(requesterId, targetUserId, tenantId);
            if (sameDepartment) {
                log.debug("Department Manager allowed (same dept): requesterId={}, targetUserId={}", 
                    requesterId, targetUserId);
                return true;
            }
            log.debug("Department Manager denied (different dept): requesterId={}, targetUserId={}", 
                requesterId, targetUserId);
            return false;
        }
        
        log.debug("No permission: requesterId={}, roleCode={}, targetUserId={}", 
            requesterId, roleCode, targetUserId);
        return false;
    }

    /**
     * Check if requester can update target user's personal profile.
     * 
     * @param requesterId User requesting the update
     * @param targetUserId User whose profile is being updated
     * @return true if allowed, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean canUpdatePersonalProfile(UUID requesterId, UUID targetUserId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        // Self-update: DENIED
        if (requesterId.equals(targetUserId)) {
            log.debug("Self-update denied: userId={}", requesterId);
            return false;
        }

        User requester = userRepository.findByTenantIdAndId(tenantId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        
        String roleCode = getRoleCode(requester);
        
        // Only Admin or HR Manager can update personal profile
        boolean allowed = "ADMIN".equals(roleCode) || 
                         "PLATFORM_ADMIN".equals(roleCode) ||
                         "HR_MANAGER".equals(roleCode) || 
                         "HR".equals(roleCode);
        
        if (allowed) {
            log.debug("Personal profile update allowed: requesterId={}, roleCode={}, targetUserId={}", 
                requesterId, roleCode, targetUserId);
        } else {
            log.debug("Personal profile update denied: requesterId={}, roleCode={}, targetUserId={}", 
                requesterId, roleCode, targetUserId);
        }
        
        return allowed;
    }

    /**
     * Check if requester can view target user's profile.
     * Users can always view their own profile.
     */
    @Transactional(readOnly = true)
    public boolean canViewProfile(UUID requesterId, UUID targetUserId, ProfileCategory category) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        
        // Self-view: ALWAYS ALLOWED
        if (requesterId.equals(targetUserId)) {
            return true;
        }

        User requester = userRepository.findByTenantIdAndId(tenantId, requesterId)
            .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        
        String roleCode = getRoleCode(requester);
        
        // Admin and HR can view everything
        if ("ADMIN".equals(roleCode) || "PLATFORM_ADMIN".equals(roleCode) || 
            "HR_MANAGER".equals(roleCode) || "HR".equals(roleCode)) {
            return true;
        }
        
        // Department Manager can view work profile of same department
        if (category == ProfileCategory.WORK_PROFILE) {
            if ("DEPT_MANAGER".equals(roleCode) || "MANAGER".equals(roleCode)) {
                return isInSameDepartment(requesterId, targetUserId, tenantId);
            }
        }
        
        // Personal profile: Only Admin/HR can view
        if (category == ProfileCategory.PERSONAL_PROFILE) {
            return false; // Already checked Admin/HR above
        }
        
        return false;
    }

    /**
     * Get role code from user (null-safe).
     */
    private String getRoleCode(User user) {
        if (user.getRole() == null) {
            return null;
        }
        return user.getRole().getRoleCode();
    }

    /**
     * Check if two users are in the same department.
     */
    private boolean isInSameDepartment(UUID user1Id, UUID user2Id, UUID tenantId) {
        User user1 = userRepository.findByTenantIdAndId(tenantId, user1Id)
            .orElseThrow(() -> new IllegalArgumentException("User1 not found"));
        User user2 = userRepository.findByTenantIdAndId(tenantId, user2Id)
            .orElseThrow(() -> new IllegalArgumentException("User2 not found"));
        
        Set<UUID> user1Depts = user1.getUserDepartments().stream()
            .map(UserDepartment::getDepartmentId)
            .collect(Collectors.toSet());
        
        Set<UUID> user2Depts = user2.getUserDepartments().stream()
            .map(UserDepartment::getDepartmentId)
            .collect(Collectors.toSet());
        
        // Check if they share any department
        user1Depts.retainAll(user2Depts);
        return !user1Depts.isEmpty();
    }
}


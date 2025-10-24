package com.fabricmanagement.common.platform.user.api.facade;

import com.fabricmanagement.common.platform.user.dto.UserDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * User Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should ONLY interact with User module through this facade.
 * This is IN-PROCESS communication (no HTTP overhead).</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class AssignmentService {
 *     private final UserFacade userFacade;  // In-process call
 *
 *     public void assignTask(UUID userId) {
 *         Optional<UserDto> user = userFacade.findById(tenantId, userId);
 *         // Use user data
 *     }
 * }
 * }</pre>
 */
public interface UserFacade {

    /**
     * Find user by ID.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @return user DTO if found
     */
    Optional<UserDto> findById(UUID tenantId, UUID userId);

    /**
     * Find user by contact value (email or phone).
     *
     * @param contactValue the contact value
     * @return user DTO if found
     */
    Optional<UserDto> findByContactValue(String contactValue);

    /**
     * Get all active users for tenant.
     *
     * @param tenantId the tenant ID
     * @return list of users
     */
    List<UserDto> findByTenant(UUID tenantId);

    /**
     * Get users by company.
     *
     * @param tenantId the tenant ID
     * @param companyId the company ID
     * @return list of users
     */
    List<UserDto> findByCompany(UUID tenantId, UUID companyId);

    /**
     * Check if user exists.
     *
     * @param tenantId the tenant ID
     * @param userId the user ID
     * @return true if exists
     */
    boolean exists(UUID tenantId, UUID userId);

    /**
     * Check if contact value is already registered.
     *
     * @param contactValue the contact value
     * @return true if exists
     */
    boolean contactExists(String contactValue);
}


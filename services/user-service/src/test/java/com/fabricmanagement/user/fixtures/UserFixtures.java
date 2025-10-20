package com.fabricmanagement.user.fixtures;

import com.fabricmanagement.shared.domain.policy.UserContext;
import com.fabricmanagement.shared.domain.role.SystemRole;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Test Data Builders for User Domain
 * 
 * Pattern: Test Data Builder (Google style)
 * - Readable test data creation
 * - Sensible defaults
 * - Easy customization
 * 
 * ⚠️ CRITICAL: Never set .id(), .version(), audit fields - Hibernate manages them!
 */
public class UserFixtures {
    
    public static final UUID TEST_TENANT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");
    public static final UUID GLOBAL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    
    // ═════════════════════════════════════════════════════
    // BASIC USER BUILDERS
    // ═════════════════════════════════════════════════════
    
    /**
     * Create basic active user
     * 
     * Defaults:
     * - Status: ACTIVE
     * - Role: USER
     * - Registration: DIRECT_REGISTRATION
     * - Context: INTERNAL
     */
    public static User createActiveUser(String firstName, String lastName, String email) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                // DON'T set .id() - Hibernate manages it!
                // DON'T set .version() - Hibernate manages it!
                // DON'T set .createdAt() - @CreatedDate manages it!
                .build();
    }
    
    /**
     * Create user with specific role
     */
    public static User createUserWithRole(String firstName, String lastName, SystemRole role) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(role) // Custom role
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create user with specific status
     */
    public static User createUserWithStatus(String firstName, String lastName, UserStatus status) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(status) // Custom status
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create user with specific tenant
     */
    public static User createUserForTenant(String firstName, String lastName, UUID tenantId) {
        return User.builder()
                .tenantId(tenantId) // Custom tenant
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    // ═════════════════════════════════════════════════════
    // ADMIN/ROLE-SPECIFIC USERS
    // ═════════════════════════════════════════════════════
    
    /**
     * Create SUPER_ADMIN user
     */
    public static User createSuperAdmin(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.SUPER_ADMIN) // Super Admin
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create TENANT_ADMIN user
     */
    public static User createTenantAdmin(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.TENANT_ADMIN) // Tenant Admin
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create MANAGER user
     */
    public static User createManager(String firstName, String lastName, UUID companyId) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.MANAGER) // Manager role
                .userContext(UserContext.INTERNAL)
                .companyId(companyId)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create regular USER
     */
    public static User createRegularUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    // ═════════════════════════════════════════════════════
    // REGISTRATION TYPE VARIANTS
    // ═════════════════════════════════════════════════════
    
    /**
     * Create self-registered user
     */
    public static User createSelfRegisteredUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.SELF_REGISTRATION) // Self-registered
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create pending approval user
     */
    public static User createPendingApprovalUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.PENDING_APPROVAL) // Pending approval
                .registrationType(RegistrationType.SELF_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create system-created user (e.g., default admin)
     */
    public static User createSystemUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.SYSTEM_CREATED) // System created
                .role(SystemRole.SUPER_ADMIN)
                .userContext(UserContext.INTERNAL)
                .createdBy("SYSTEM")
                .build();
    }
    
    // ═════════════════════════════════════════════════════
    // USER CONTEXT VARIANTS
    // ═════════════════════════════════════════════════════
    
    /**
     * Create internal user (employee)
     */
    public static User createInternalUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .jobTitle("Internal Employee")
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create viewer user
     */
    public static User createViewerUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.VIEWER) // Viewer role
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    // ═════════════════════════════════════════════════════
    // COMPLEX USERS (With All Fields)
    // ═════════════════════════════════════════════════════
    
    /**
     * Create fully populated user (all fields)
     */
    public static User createCompleteUser(String firstName, String lastName) {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", "dark");
        preferences.put("language", "en");
        preferences.put("notifications", true);
        
        Map<String, Object> settings = new HashMap<>();
        settings.put("timezone", "UTC");
        settings.put("dateFormat", "YYYY-MM-DD");
        
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .companyId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .stationId(UUID.randomUUID())
                .jobTitle("Senior Engineer")
                .preferences(preferences)
                .settings(settings)
                .functions(Arrays.asList("PRODUCTION", "QUALITY", "INVENTORY"))
                .lastLoginAt(LocalDateTime.now().minusDays(1))
                .lastLoginIp("192.168.1.100")
                .passwordHash("$2a$10$hashedPasswordExample")
                .createdBy("TEST_USER")
                .build();
    }
    
    // ═════════════════════════════════════════════════════
    // SPECIAL CASES
    // ═════════════════════════════════════════════════════
    
    /**
     * Create deleted user
     */
    public static User createDeletedUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.DELETED) // Deleted status
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .deleted(true)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create suspended user
     */
    public static User createSuspendedUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.SUSPENDED) // Suspended status
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    /**
     * Create inactive user
     */
    public static User createInactiveUser(String firstName, String lastName) {
        return User.builder()
                .tenantId(TEST_TENANT_ID)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(firstName + " " + lastName)
                .status(UserStatus.INACTIVE) // Inactive status
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(SystemRole.USER)
                .userContext(UserContext.INTERNAL)
                .createdBy("TEST_USER")
                .build();
    }
    
    // ═════════════════════════════════════════════════════
    // BATCH USERS
    // ═════════════════════════════════════════════════════
    
    /**
     * Create multiple users (for bulk testing)
     */
    public static List<User> createMultipleUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(createActiveUser(
                    "User" + i,
                    "Test" + i,
                    "user" + i + "@test.com"
            ));
        }
        return users;
    }
    
    /**
     * Create users with different roles
     */
    public static List<User> createUsersWithDifferentRoles() {
        return Arrays.asList(
                createSuperAdmin("Super", "Admin"),
                createTenantAdmin("Tenant", "Admin"),
                createManager("Manager", "Admin", UUID.randomUUID()),
                createRegularUser("Regular", "User")
        );
    }
    
    /**
     * Create users with different statuses
     */
    public static List<User> createUsersWithDifferentStatuses() {
        return Arrays.asList(
                createUserWithStatus("Active", "User", UserStatus.ACTIVE),
                createUserWithStatus("Pending", "User", UserStatus.PENDING_VERIFICATION),
                createUserWithStatus("Suspended", "User", UserStatus.SUSPENDED),
                createUserWithStatus("Inactive", "User", UserStatus.INACTIVE)
        );
    }
    
    // ═════════════════════════════════════════════════════
    // TENANT-SPECIFIC USERS
    // ═════════════════════════════════════════════════════
    
    /**
     * Create users for multiple tenants
     */
    public static Map<UUID, List<User>> createUsersForMultipleTenants(int tenantCount, int usersPerTenant) {
        Map<UUID, List<User>> usersByTenant = new HashMap<>();
        
        for (int t = 1; t <= tenantCount; t++) {
            UUID tenantId = UUID.randomUUID();
            List<User> users = new ArrayList<>();
            
            for (int u = 1; u <= usersPerTenant; u++) {
                users.add(createUserForTenant(
                        "Tenant" + t + "User" + u,
                        "Test",
                        tenantId
                ));
            }
            
            usersByTenant.put(tenantId, users);
        }
        
        return usersByTenant;
    }
}


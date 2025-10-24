package com.fabricmanagement.shared.application.context;

import com.fabricmanagement.shared.domain.policy.CompanyType;
import com.fabricmanagement.shared.domain.policy.DataScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityContext
 * 
 * Tests both legacy (V1.0) and new (V2.0) fields
 */
@DisplayName("SecurityContext Tests")
class SecurityContextTest {
    
    @Test
    @DisplayName("Should create context with legacy fields only (backward compatibility)")
    void shouldCreateContextWithLegacyFieldsOnly() {
        // Given
        UUID tenantId = UUID.randomUUID();
        String userId = "user-123";
        String[] roles = {"ADMIN", "MANAGER"};
        
        // When
        SecurityContext context = SecurityContext.builder()
            .tenantId(tenantId)
            .userId(userId)
            .roles(roles)
            .build();
        
        // Then
        assertEquals(tenantId, context.getTenantId());
        assertEquals(userId, context.getUserId());
        assertArrayEquals(roles, context.getRoles());
        
        // New fields should be null
        assertNull(context.getCompanyId());
        assertNull(context.getCompanyType());
        assertNull(context.getDepartmentId());
    }
    
    @Test
    @DisplayName("Should create context with all fields (V2.0)")
    void shouldCreateContextWithAllFields() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        
        // When
        SecurityContext context = SecurityContext.builder()
            .tenantId(tenantId)
            .userId("user-123")
            .roles(new String[]{"OPERATOR"})
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .departmentId(departmentId)
            .jobTitle("Dokumacı")
            .permissions(Arrays.asList("READ:USER/SELF", "WRITE:CONTACT/COMPANY"))
            .defaultScope(DataScope.COMPANY)
            .build();
        
        // Then
        assertNotNull(context.getCompanyId());
        assertEquals(CompanyType.INTERNAL, context.getCompanyType());
        assertEquals(departmentId, context.getDepartmentId());
        assertEquals("Dokumacı", context.getJobTitle());
        assertEquals(2, context.getPermissions().size());
        assertEquals(DataScope.COMPANY, context.getDefaultScope());
    }
    
    @Test
    @DisplayName("Should check role correctly")
    void shouldCheckRoleCorrectly() {
        SecurityContext context = SecurityContext.builder()
            .roles(new String[]{"ADMIN", "MANAGER"})
            .build();
        
        assertTrue(context.hasRole("ADMIN"));
        assertTrue(context.hasRole("MANAGER"));
        assertFalse(context.hasRole("USER"));
    }
    
    @Test
    @DisplayName("Should check role with ROLE_ prefix")
    void shouldCheckRoleWithPrefix() {
        SecurityContext context = SecurityContext.builder()
            .roles(new String[]{"ROLE_ADMIN"})
            .build();
        
        assertTrue(context.hasRole("ADMIN"));
        assertTrue(context.hasRole("ROLE_ADMIN"));
    }
    
    @Test
    @DisplayName("Should check multiple roles with hasAnyRole")
    void shouldCheckMultipleRoles() {
        SecurityContext context = SecurityContext.builder()
            .roles(new String[]{"OPERATOR"})
            .build();
        
        assertTrue(context.hasAnyRole("ADMIN", "OPERATOR"));
        assertTrue(context.hasAnyRole("OPERATOR", "USER"));
        assertFalse(context.hasAnyRole("ADMIN", "MANAGER"));
    }
    
    @Test
    @DisplayName("Should handle null roles gracefully")
    void shouldHandleNullRolesGracefully() {
        SecurityContext context = SecurityContext.builder()
            .roles(null)
            .build();
        
        assertFalse(context.hasRole("ADMIN"));
        assertFalse(context.hasAnyRole("ADMIN", "USER"));
    }
    
    @Test
    @DisplayName("Should check permission correctly")
    void shouldCheckPermissionCorrectly() {
        SecurityContext context = SecurityContext.builder()
            .permissions(Arrays.asList("READ:USER/SELF", "WRITE:CONTACT/COMPANY"))
            .build();
        
        assertTrue(context.hasPermission("READ:USER/SELF"));
        assertTrue(context.hasPermission("WRITE:CONTACT/COMPANY"));
        assertFalse(context.hasPermission("DELETE:USER/COMPANY"));
    }
    
    @Test
    @DisplayName("Should handle null permissions gracefully")
    void shouldHandleNullPermissionsGracefully() {
        SecurityContext context = SecurityContext.builder()
            .permissions(null)
            .build();
        
        assertFalse(context.hasPermission("READ:USER/SELF"));
    }
    
    @Test
    @DisplayName("Should identify INTERNAL company type")
    void shouldIdentifyInternalCompanyType() {
        SecurityContext internal = SecurityContext.builder()
            .companyType(CompanyType.INTERNAL)
            .build();
        
        assertTrue(internal.isInternal());
        assertFalse(internal.isExternal());
        
        SecurityContext customer = SecurityContext.builder()
            .companyType(CompanyType.CUSTOMER)
            .build();
        
        assertFalse(customer.isInternal());
        assertTrue(customer.isExternal());
    }
    
    @Test
    @DisplayName("Should handle null company type gracefully")
    void shouldHandleNullCompanyTypeGracefully() {
        SecurityContext context = SecurityContext.builder()
            .companyType(null)
            .build();
        
        assertFalse(context.isInternal());
        assertFalse(context.isExternal());
    }
    
    @Test
    @DisplayName("Should return effective scope (default to SELF)")
    void shouldReturnEffectiveScopeDefaultToSelf() {
        // With scope specified
        SecurityContext withScope = SecurityContext.builder()
            .defaultScope(DataScope.COMPANY)
            .build();
        assertEquals(DataScope.COMPANY, withScope.getEffectiveScope());
        
        // Without scope (null) → defaults to SELF
        SecurityContext withoutScope = SecurityContext.builder()
            .defaultScope(null)
            .build();
        assertEquals(DataScope.SELF, withoutScope.getEffectiveScope());
    }
    
    @Test
    @DisplayName("Should check department assignment")
    void shouldCheckDepartmentAssignment() {
        SecurityContext withDept = SecurityContext.builder()
            .departmentId(UUID.randomUUID())
            .build();
        assertTrue(withDept.hasDepartment());
        
        SecurityContext withoutDept = SecurityContext.builder()
            .departmentId(null)
            .build();
        assertFalse(withoutDept.hasDepartment());
    }
    
    @Test
    @DisplayName("Should support builder pattern with all fields")
    void shouldSupportBuilderPatternWithAllFields() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        
        SecurityContext context = SecurityContext.builder()
            .tenantId(tenantId)
            .userId("user-123")
            .roles(new String[]{"ADMIN"})
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .departmentId(departmentId)
            .jobTitle("Manager")
            .permissions(List.of("READ:USER/COMPANY"))
            .defaultScope(DataScope.COMPANY)
            .build();
        
        assertNotNull(context);
        assertEquals(tenantId, context.getTenantId());
        assertEquals(companyId, context.getCompanyId());
        assertEquals(CompanyType.INTERNAL, context.getCompanyType());
        assertEquals(departmentId, context.getDepartmentId());
        assertEquals("Manager", context.getJobTitle());
        assertEquals(1, context.getPermissions().size());
        assertEquals(DataScope.COMPANY, context.getDefaultScope());
    }
}


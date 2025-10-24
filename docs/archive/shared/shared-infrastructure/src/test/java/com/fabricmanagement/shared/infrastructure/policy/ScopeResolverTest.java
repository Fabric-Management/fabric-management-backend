package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.resolver.ScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScopeResolver
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@DisplayName("ScopeResolver Tests")
class ScopeResolverTest {
    
    private ScopeResolver resolver;
    private UUID userId;
    private UUID companyId;
    private UUID otherUserId;
    private UUID otherCompanyId;
    
    @BeforeEach
    void setUp() {
        resolver = new ScopeResolver();
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        otherCompanyId = UUID.randomUUID();
    }
    
    @Test
    @DisplayName("SELF scope should allow when accessing own data")
    void selfScopeShouldAllowOwnData() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.SELF)
            .resourceOwnerId(userId) // Same as userId
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("SELF scope should deny when accessing other user's data")
    void selfScopeShouldDenyOtherUserData() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.SELF)
            .resourceOwnerId(otherUserId) // Different from userId
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("scope_violation_self_not_owner", denial);
    }
    
    @Test
    @DisplayName("COMPANY scope should allow when accessing same company data")
    void companyScopeShouldAllowSameCompanyData() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.COMPANY)
            .resourceCompanyId(companyId) // Same as companyId
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("COMPANY scope should deny when accessing different company data")
    void companyScopeShouldDenyDifferentCompanyData() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.COMPANY)
            .resourceCompanyId(otherCompanyId) // Different from companyId
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("scope_violation_company_different_company", denial);
    }
    
    @Test
    @DisplayName("COMPANY scope should deny when user has no company")
    void companyScopeShouldDenyNoCompany() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(null) // No company
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.COMPANY)
            .resourceCompanyId(companyId)
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("scope_violation_company_user_no_company", denial);
    }
    
    @Test
    @DisplayName("CROSS_COMPANY scope should allow for INTERNAL users")
    void crossCompanyScopeShouldAllowInternal() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.CROSS_COMPANY)
            .resourceCompanyId(otherCompanyId)
            .roles(List.of("ADMIN"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("CROSS_COMPANY scope should deny for EXTERNAL users without relationship")
    void crossCompanyScopeShouldDenyExternal() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.CUSTOMER) // External
            .scope(DataScope.CROSS_COMPANY)
            .resourceCompanyId(otherCompanyId)
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("scope_violation_cross_company_no_relationship", denial);
    }
    
    @Test
    @DisplayName("GLOBAL scope should allow for SUPER_ADMIN")
    void globalScopeShouldAllowSuperAdmin() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.GLOBAL)
            .roles(List.of("SUPER_ADMIN"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNull(denial);
    }
    
    @Test
    @DisplayName("GLOBAL scope should deny for non-admin")
    void globalScopeShouldDenyNonAdmin() {
        // Given
        PolicyContext context = PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(CompanyType.INTERNAL)
            .scope(DataScope.GLOBAL)
            .roles(List.of("USER"))
            .build();
        
        // When
        String denial = resolver.validateScope(context);
        
        // Then
        assertNotNull(denial);
        assertEquals("scope_violation_global_not_admin", denial);
    }
    
    @Test
    @DisplayName("canAccess should return true for SELF scope with matching owner")
    void canAccessSelfScope() {
        // When
        boolean result = resolver.canAccess(userId, userId, companyId, companyId, DataScope.SELF);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    @DisplayName("canAccess should return false for SELF scope with different owner")
    void canAccessSelfScopeDifferentOwner() {
        // When
        boolean result = resolver.canAccess(userId, otherUserId, companyId, companyId, DataScope.SELF);
        
        // Then
        assertFalse(result);
    }
    
    @Test
    @DisplayName("canAccess should return true for COMPANY scope with matching company")
    void canAccessCompanyScope() {
        // When
        boolean result = resolver.canAccess(userId, otherUserId, companyId, companyId, DataScope.COMPANY);
        
        // Then
        assertTrue(result);
    }
    
    @Test
    @DisplayName("inferScopeFromEndpoint should return SELF for /me endpoints")
    void inferScopeSelfForMe() {
        // When
        DataScope scope = resolver.inferScopeFromEndpoint("/api/users/me");
        
        // Then
        assertEquals(DataScope.SELF, scope);
    }
    
    @Test
    @DisplayName("inferScopeFromEndpoint should return COMPANY for /company endpoints")
    void inferScopeCompanyForCompany() {
        // When
        DataScope scope = resolver.inferScopeFromEndpoint("/api/company/users");
        
        // Then
        assertEquals(DataScope.COMPANY, scope);
    }
    
    @Test
    @DisplayName("inferScopeFromEndpoint should return GLOBAL for /admin endpoints")
    void inferScopeGlobalForAdmin() {
        // When
        DataScope scope = resolver.inferScopeFromEndpoint("/api/admin/settings");
        
        // Then
        assertEquals(DataScope.GLOBAL, scope);
    }
    
    @Test
    @DisplayName("inferScopeFromEndpoint should return COMPANY as default")
    void inferScopeCompanyDefault() {
        // When
        DataScope scope = resolver.inferScopeFromEndpoint("/api/contacts");
        
        // Then
        assertEquals(DataScope.COMPANY, scope);
    }
}


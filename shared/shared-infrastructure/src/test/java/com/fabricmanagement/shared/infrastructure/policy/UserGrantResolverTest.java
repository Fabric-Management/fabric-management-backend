package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.resolver.UserGrantResolver;
import com.fabricmanagement.shared.infrastructure.policy.repository.UserPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for UserGrantResolver
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Grant Resolver Tests")
class UserGrantResolverTest {
    
    @Mock
    private UserPermissionRepository userPermissionRepository;
    
    private UserGrantResolver resolver;
    
    @BeforeEach
    void setUp() {
        resolver = new UserGrantResolver(userPermissionRepository);
    }
    
    @Test
    @DisplayName("Should return null when no deny grants exist")
    void shouldReturnNullWhenNoDenyGrants() {
        // Given
        PolicyContext context = createTestContext();
        when(userPermissionRepository.findDenyPermissions(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());
        
        // When
        String result = resolver.checkUserDeny(context);
        
        // Then
        assertNull(result, "Should return null when no deny grants");
    }
    
    @Test
    @DisplayName("Should return denial when user has deny grant")
    void shouldReturnDenialWhenUserHasDenyGrant() {
        // Given
        PolicyContext context = createTestContext();
        
        UserPermission denyGrant = UserPermission.builder()
            .id(UUID.randomUUID())
            .userId(context.getUserId())
            .endpoint(context.getEndpoint())
            .operation(context.getOperation())
            .permissionType(PermissionType.DENY)
            .status("ACTIVE")
            .build();
        
        when(userPermissionRepository.findDenyPermissions(any(), any(), any(), any()))
            .thenReturn(List.of(denyGrant));
        
        // When
        String result = resolver.checkUserDeny(context);
        
        // Then
        assertNotNull(result, "Should return denial");
        assertTrue(result.contains("user_grant"), "Reason should mention user grant");
    }
    
    @Test
    @DisplayName("Should return false when no allow grants exist")
    void shouldReturnFalseWhenNoAllowGrants() {
        // Given
        PolicyContext context = createTestContext();
        when(userPermissionRepository.findAllowPermissions(any(), any(), any(), any()))
            .thenReturn(Collections.emptyList());
        
        // When
        boolean result = resolver.hasUserAllow(context);
        
        // Then
        assertFalse(result, "Should return false when no allow grants");
    }
    
    @Test
    @DisplayName("Should work without repository")
    void shouldWorkWithoutRepository() {
        // Given
        UserGrantResolver resolverWithoutRepo = new UserGrantResolver(null);
        PolicyContext context = createTestContext();
        
        // When & Then
        assertNull(resolverWithoutRepo.checkUserDeny(context));
        assertFalse(resolverWithoutRepo.hasUserAllow(context));
        assertEquals(0, resolverWithoutRepo.getEffectiveGrantsCount(UUID.randomUUID()));
    }
    
    private PolicyContext createTestContext() {
        return PolicyContext.builder()
            .userId(UUID.randomUUID())
            .companyId(UUID.randomUUID())
            .companyType(CompanyType.INTERNAL)
            .endpoint("/api/v1/users")
            .httpMethod("DELETE")
            .operation(OperationType.DELETE)
            .scope(DataScope.COMPANY)
            .correlationId(UUID.randomUUID().toString())
            .requestId(UUID.randomUUID().toString())
            .build();
    }
}


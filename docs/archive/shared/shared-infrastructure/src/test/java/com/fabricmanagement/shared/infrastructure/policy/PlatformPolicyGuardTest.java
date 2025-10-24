package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.guard.PlatformPolicyGuard;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PlatformPolicyGuard
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Platform Policy Guard Tests")
class PlatformPolicyGuardTest {
    
    @Mock
    private PolicyRegistryRepository policyRegistryRepository;
    
    private PlatformPolicyGuard guard;
    
    @BeforeEach
    void setUp() {
        guard = new PlatformPolicyGuard(policyRegistryRepository);
    }
    
    @Test
    @DisplayName("Should allow when no policy exists")
    void shouldAllowWhenNoPolicyExists() {
        // Given
        PolicyContext context = createTestContext(CompanyType.CUSTOMER);
        when(policyRegistryRepository.findByEndpointAndOperationAndActiveTrue(any(), any()))
            .thenReturn(Optional.empty());
        
        // When
        String result = guard.checkPlatformPolicy(context);
        
        // Then
        assertNull(result, "Should allow when no policy exists");
    }
    
    @Test
    @DisplayName("Should allow when no repository available")
    void shouldAllowWhenNoRepository() {
        // Given
        PlatformPolicyGuard guardWithoutRepo = new PlatformPolicyGuard(null);
        PolicyContext context = createTestContext(CompanyType.INTERNAL);
        
        // When
        String result = guardWithoutRepo.checkPlatformPolicy(context);
        
        // Then
        assertNull(result, "Should allow when repository not available");
    }
    
    @Test
    @DisplayName("Should deny when company type not allowed")
    void shouldDenyWhenCompanyTypeNotAllowed() {
        // Given
        PolicyContext context = createTestContext(CompanyType.SUPPLIER);
        
        PolicyRegistry policy = PolicyRegistry.builder()
            .id(UUID.randomUUID())
            .endpoint("/api/v1/users")
            .operation(OperationType.WRITE)
            .allowedCompanyTypes(List.of("INTERNAL", "CUSTOMER"))
            .active(true)
            .build();
        
        when(policyRegistryRepository.findByEndpointAndOperationAndActiveTrue(any(), any()))
            .thenReturn(Optional.of(policy));
        
        // When
        String result = guard.checkPlatformPolicy(context);
        
        // Then
        assertNotNull(result, "Should deny when company type not allowed");
        assertTrue(result.contains("platform_policy"), "Reason should mention platform policy");
    }
    
    private PolicyContext createTestContext(CompanyType companyType) {
        return PolicyContext.builder()
            .userId(UUID.randomUUID())
            .companyId(UUID.randomUUID())
            .companyType(companyType)
            .endpoint("/api/v1/users")
            .httpMethod("POST")
            .operation(OperationType.WRITE)
            .scope(DataScope.COMPANY)
            .correlationId(UUID.randomUUID().toString())
            .requestId(UUID.randomUUID().toString())
            .build();
    }
}


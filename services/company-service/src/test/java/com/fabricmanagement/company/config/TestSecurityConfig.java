package com.fabricmanagement.company.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Test Security Configuration
 *
 * Provides mock security context for testing
 */
@TestConfiguration
public class TestSecurityConfig {

    private static final UUID TEST_TENANT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_USER_ID = "test-user-001";

    /**
     * Sets up test security context
     */
    @Bean
    @Primary
    public SecurityContext testSecurityContext() {
        // Create mock authentication with tenant ID
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                TEST_USER_ID,
                null,
                List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_COMPANY_MANAGER"),
                    new SimpleGrantedAuthority("ROLE_USER")
                )
            );

        // Set tenant ID as details (matching SecurityContextHolder expectations)
        authentication.setDetails(TEST_TENANT_ID.toString());
        authentication.setAuthenticated(true);

        // Set in Spring Security context
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        return context;
    }
}

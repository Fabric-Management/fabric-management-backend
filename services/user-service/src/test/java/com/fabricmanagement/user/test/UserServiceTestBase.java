package com.fabricmanagement.user.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base test class for User Service tests
 * 
 * Provides common test configuration and utilities
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public abstract class UserServiceTestBase {
    
    // Common test utilities and configuration
    protected static final String TEST_EMAIL = "test@example.com";
    protected static final String TEST_PHONE = "+905551234567";
    protected static final String TEST_FIRST_NAME = "John";
    protected static final String TEST_LAST_NAME = "Doe";
    protected static final String TEST_PASSWORD_HASH = "hashedPassword123";
    protected static final String TEST_TENANT_ID = "550e8400-e29b-41d4-a716-446655440000";
}

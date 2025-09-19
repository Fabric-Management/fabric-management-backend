package com.fabricmanagement.common.core.test.util;

import java.util.UUID;

/**
 * Utility class providing consistent UUID values for testing.
 * Uses predictable UUIDs for reproducible tests.
 */
public final class TestUUIDs {
    
    // Prevent instantiation
    private TestUUIDs() {}
    
    // Fixed UUIDs for test consistency
    public static final UUID USER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID USER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID USER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    
    public static final UUID TENANT_1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID TENANT_2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
    
    public static final UUID COMPANY_1 = UUID.fromString("20000000-0000-0000-0000-000000000001");
    public static final UUID COMPANY_2 = UUID.fromString("20000000-0000-0000-0000-000000000002");
    
    public static final UUID CONTACT_1 = UUID.fromString("30000000-0000-0000-0000-000000000001");
    public static final UUID CONTACT_2 = UUID.fromString("30000000-0000-0000-0000-000000000002");
    
    public static final UUID SESSION_1 = UUID.fromString("40000000-0000-0000-0000-000000000001");
    public static final UUID SESSION_2 = UUID.fromString("40000000-0000-0000-0000-000000000002");
    
    /**
     * Generate a predictable UUID based on a seed value.
     * Useful for creating multiple related test UUIDs.
     */
    public static UUID generateTestUUID(String prefix, int sequence) {
        return UUID.fromString(String.format("%s000000-0000-0000-0000-%012d", 
            prefix, sequence));
    }
    
    /**
     * Generate a random UUID for tests that don't need predictability.
     */
    public static UUID random() {
        return UUID.randomUUID();
    }
}
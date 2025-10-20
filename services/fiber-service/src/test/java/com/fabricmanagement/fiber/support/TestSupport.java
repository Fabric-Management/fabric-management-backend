package com.fabricmanagement.fiber.support;

import java.math.BigDecimal;
import java.util.UUID;

public final class TestSupport {

    private TestSupport() {}

    // Common UUIDs
    public static final UUID GLOBAL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // Common users
    public static final String TEST_USER = "TEST_USER";
    public static final String SYSTEM_USER = "SYSTEM";

    // Common fiber codes/names
    public static final String CODE_COTTON = "CO";
    public static final String NAME_COTTON = "Cotton";
    public static final String CODE_POLYESTER = "PE";
    public static final String NAME_POLYESTER = "Polyester";

    // Common composition percentages
    public static final BigDecimal PCT_60 = BigDecimal.valueOf(60.0);
    public static final BigDecimal PCT_40 = BigDecimal.valueOf(40.0);

    // Helper: stable random UUID for tests needing distinct IDs
    public static UUID newId() {
        return UUID.randomUUID();
    }
}



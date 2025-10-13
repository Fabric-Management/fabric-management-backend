package com.fabricmanagement.shared.infrastructure.constants;

public final class SecurityRoles {
    
    private SecurityRoles() {
        throw new UnsupportedOperationException("Constants class");
    }
    
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String USER = "USER";
    public static final String VIEWER = "VIEWER";
    public static final String COMPANY_MANAGER = "COMPANY_MANAGER";
    
    public static final String[] ALL_ADMIN_ROLES = {
        SUPER_ADMIN,
        TENANT_ADMIN
    };
    
    public static final String[] ALL_MANAGEMENT_ROLES = {
        SUPER_ADMIN,
        TENANT_ADMIN,
        MANAGER,
        COMPANY_MANAGER
    };
}


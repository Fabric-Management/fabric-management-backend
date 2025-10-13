package com.fabricmanagement.shared.domain.role;

public enum SystemRole {
    
    SUPER_ADMIN(RoleScope.PLATFORM, 100),
    TENANT_ADMIN(RoleScope.TENANT, 80),
    MANAGER(RoleScope.TENANT, 60),
    USER(RoleScope.TENANT, 40),
    VIEWER(RoleScope.TENANT, 20);
    
    private final RoleScope scope;
    private final int priority;
    
    SystemRole(RoleScope scope, int priority) {
        this.scope = scope;
        this.priority = priority;
    }
    
    public RoleScope getScope() {
        return scope;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public boolean hasHigherOrEqualPriority(SystemRole other) {
        return this.priority >= other.priority;
    }
    
    public boolean isPlatformRole() {
        return scope == RoleScope.PLATFORM;
    }
    
    public boolean isTenantRole() {
        return scope == RoleScope.TENANT;
    }
    
    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == TENANT_ADMIN;
    }
}


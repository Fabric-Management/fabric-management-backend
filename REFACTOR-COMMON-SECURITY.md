# Common-Security Module Refactoring Instructions

## Files to Remove (❌)

### 1. Domain Models (belong in auth-service/user-service)
```bash
rm /Users/user/Coding/fabric-management/fabric-management-backend/common/common-security/src/main/java/com/fabricmanagement/common/security/model/AuthenticatedUser.java
rm /Users/user/Coding/fabric-management/fabric-management-backend/common/common-security/src/main/java/com/fabricmanagement/common/security/model/Role.java
rmdir /Users/user/Coding/fabric-management/fabric-management-backend/common/common-security/src/main/java/com/fabricmanagement/common/security/model
```

### 2. Runtime Configuration Files (belong in individual services)
```bash
rm -rf /Users/user/Coding/fabric-management/fabric-management-backend/common/common-security/src/main/resources
```

## Files to Keep (✅)

The following structure should remain after cleanup:

```
common-security/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── fabricmanagement/
                    └── common/
                        └── security/
                            ├── config/
                            │   ├── SecurityAutoConfiguration.java
                            │   └── JwtProperties.java
                            ├── context/
                            │   └── SecurityContextUtil.java
                            ├── exception/
                            │   ├── JwtTokenExpiredException.java
                            │   ├── JwtTokenInvalidException.java
                            │   └── UnauthorizedException.java
                            └── jwt/
                                ├── JwtAuthenticationFilter.java
                                ├── JwtTokenProvider.java
                                └── JwtUtil.java
```

## Execute Cleanup
Run the cleanup script:
```bash
chmod +x cleanup-common-security.sh && ./cleanup-common-security.sh
```

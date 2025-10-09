# 🔐 Policy-Based Authorization - Implementation TODO

**Start Date:** 2025-10-08  
**Completion Date:** 2025-10-09 ✅  
**Duration:** 2 days (Ahead of schedule!)  
**Architecture:** PEP/PDP + Company Type + RBAC + User Grants  
**Principles:** Clean Architecture + DDD + SOLID + UUID Type Safety  
**Last Updated:** 2025-10-09 14:52 UTC+1

---

## 📋 Implementation Phases

### ✅ Phase 0: Pre-Implementation - COMPLETE

- [x] Gap analysis completed
- [x] Architecture review
- [x] Principles documented
- [x] TODO created

### ✅ Phase 1: Foundation - COMPLETE

- [x] 6 Policy enums created
- [x] Database migrations (V2-V7)
- [x] Domain entities created
- [x] JWT enhancements (companyId)
- [x] SecurityContext extended

### ✅ Phase 2: Policy Engine - COMPLETE

- [x] PolicyEngine (PDP) implemented
- [x] CompanyTypeGuard (guardrails)
- [x] PlatformPolicyGuard (platform policies)
- [x] ScopeResolver (scope validation)
- [x] UserGrantResolver (user grants)
- [x] PolicyCache (in-memory)
- [x] PolicyAuditService (async logging)
- [x] 3 Repository interfaces
- [x] PolicyConstants (centralized)
- [x] 62 unit tests (100% pass)

### ✅ Phase 3: Gateway Integration - COMPLETE

- [x] PolicyEnforcementFilter (PEP)
- [x] Optional repository pattern
- [x] JWT companyId claim
- [x] X-Company-Id header propagation
- [x] Reactive compatibility

### ✅ Phase 4: Advanced Settings API - COMPLETE

- [x] UserPermissionController
- [x] UserPermissionService
- [x] DTOs & Mappers
- [x] CRUD endpoints

### ✅ Phase 5: Audit Log API - COMPLETE

- [x] PolicyAuditController
- [x] PolicyAuditQueryService
- [x] Statistics endpoints
- [x] Correlation ID tracing

---

## 🎯 Phase 1: Foundation - Data Model & Enums (Week 1)

**Goal:** Core domain model extensions without breaking existing functionality

### 📦 1.1 - Create Policy Domain Enums (Day 1)

**Location:** `shared/shared-domain/src/main/java/com/fabricmanagement/shared/domain/policy/`

#### ✅ Tasks:

- [ ] **Create `CompanyType.java`** enum

  ```java
  // shared-domain/policy/CompanyType.java
  public enum CompanyType {
      INTERNAL("Internal Company"),
      CUSTOMER("Customer Company"),
      SUPPLIER("Supplier Company"),
      SUBCONTRACTOR("Subcontractor");

      private final String displayLabel;

      CompanyType(String displayLabel) {
          this.displayLabel = displayLabel;
      }

      public String getDisplayLabel() {
          return displayLabel;
      }

      public boolean isInternal() {
          return this == INTERNAL;
      }

      public boolean isExternal() {
          return !isInternal();
      }
  }
  ```

  - ⚠️ **Principle:** Simple enum with display labels (UI/logging friendly)
  - ⚠️ **Validation:** Must be serializable for Kafka events
  - 💡 **Pro Tip:** `displayLabel` makes UI/API integration easier

- [ ] **Create `UserContext.java`** enum

  ```java
  // shared-domain/policy/UserContext.java
  public enum UserContext {
      INTERNAL,      // Kendi firmamızın çalışanı
      CUSTOMER,      // Müşteri firma çalışanı
      SUPPLIER,      // Tedarikçi firma çalışanı
      SUBCONTRACTOR  // Fason üretici çalışanı
  }
  ```

- [ ] **Create `DepartmentType.java`** enum

  ```java
  // shared-domain/policy/DepartmentType.java
  public enum DepartmentType {
      PRODUCTION,     // Üretim (Dokuma, Boyama, etc.)
      QUALITY,        // Kalite Kontrol
      WAREHOUSE,      // Depo
      FINANCE,        // Muhasebe
      SALES,          // Satış
      PURCHASING,     // Satın Alma
      HR,             // İnsan Kaynakları
      IT,             // Bilgi İşlem
      MANAGEMENT      // Yönetim
  }
  ```

- [ ] **Create `OperationType.java`** enum

  ```java
  // shared-domain/policy/OperationType.java
  public enum OperationType {
      READ,    // Görüntüleme
      WRITE,   // Oluşturma/Güncelleme
      DELETE,  // Silme
      APPROVE, // Onaylama
      EXPORT,  // Dışa aktarma
      MANAGE   // Yönetim (grant/revoke)
  }
  ```

- [ ] **Create `DataScope.java`** enum

  ```java
  // shared-domain/policy/DataScope.java
  public enum DataScope {
      SELF,          // Sadece kendi verileri
      COMPANY,       // Şirketteki veriler
      CROSS_COMPANY, // Birden fazla şirket
      GLOBAL         // Tüm sistem (Super Admin)
  }
  ```

- [ ] **Create `PermissionType.java`** enum
  ```java
  // shared-domain/policy/PermissionType.java
  public enum PermissionType {
      ALLOW,  // İzin ver
      DENY    // İzin verme (öncelikli)
  }
  ```

**Checklist:**

- [ ] All enums in `shared-domain/policy/` package
- [ ] Each enum has JavaDoc
- [ ] No business logic in enums (pure data)
- [ ] Serializable (Kafka compatibility)
- [ ] Unit tests created

**Estimated Time:** 2 hours

---

### 📦 1.2 - Extend SecurityContext (Day 1)

**Location:** `shared/shared-application/src/main/java/com/fabricmanagement/shared/application/context/`

#### ✅ Tasks:

- [ ] **Extend `SecurityContext.java`**

  ```java
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public class SecurityContext {

      // 🔥 EXISTING (keep as-is)
      private UUID tenantId;
      private String userId;
      private String[] roles;

      // 🔥 NEW FIELDS
      private UUID companyId;              // Hangi firma
      private CompanyType companyType;     // INTERNAL/CUSTOMER/SUPPLIER
      private UUID departmentId;           // Hangi departman (nullable for external)
      private String jobTitle;             // "Dokumacı", "Muhasebeci"
      private List<String> permissions;    // Effective permissions cache (⚠️ can bloat JWT)
      private DataScope defaultScope;      // Varsayılan scope

      // ⚠️ NOTE: If JWT payload becomes too large (>8KB), consider storing only
      // policyVersion hash and fetching permissions from PDP/Redis

      // Helper methods
      public boolean hasPermission(String permission) {
          return permissions != null && permissions.contains(permission);
      }

      public boolean hasAnyRole(String... requiredRoles) {
          if (roles == null) return false;
          return Arrays.stream(requiredRoles)
              .anyMatch(role -> Arrays.asList(roles).contains(role));
      }

      public boolean isInternal() {
          return CompanyType.INTERNAL.equals(companyType);
      }

      public boolean isExternal() {
          return !isInternal();
      }
  }
  ```

**Checklist:**

- [ ] Backward compatible (existing fields unchanged)
- [ ] Null-safe helper methods
- [ ] JavaDoc for new fields
- [ ] Unit tests updated
- [ ] No breaking changes to existing usages

**Estimated Time:** 1 hour

---

### 📦 1.3 - Database Schema Changes (Day 2-3)

#### 🗄️ 1.3.1 - User Table Extensions

**Location:** `services/user-service/src/main/resources/db/migration/`

- [ ] **Create `V3__add_policy_fields_to_users.sql`**

  ```sql
  -- Add policy-related fields to users table
  ALTER TABLE users
  ADD COLUMN company_id UUID,
  ADD COLUMN department_id UUID,
  ADD COLUMN station_id UUID,
  ADD COLUMN job_title VARCHAR(100),
  ADD COLUMN user_context VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
  ADD COLUMN functions TEXT[];

  -- Add foreign key constraint
  ALTER TABLE users
  ADD CONSTRAINT fk_users_company
      FOREIGN KEY (company_id) REFERENCES companies(id)
      ON DELETE SET NULL;

  -- Create index for performance
  CREATE INDEX idx_users_company_id ON users(company_id);
  CREATE INDEX idx_users_department_id ON users(department_id);
  CREATE INDEX idx_users_user_context ON users(user_context);

  -- Add check constraint
  ALTER TABLE users
  ADD CONSTRAINT chk_user_context
      CHECK (user_context IN ('INTERNAL', 'CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'));
  ```

- [ ] **Update `User.java` entity**

  ```java
  @Entity
  @Table(name = "users")
  public class User extends BaseEntity {

      // 🔥 EXISTING FIELDS (keep as-is)
      @Column(name = "tenant_id", nullable = false)
      private UUID tenantId;

      private String firstName;
      private String lastName;
      private String role;
      // ... other existing fields

      // 🔥 NEW FIELDS
      @Column(name = "company_id")
      private UUID companyId;

      @Column(name = "department_id")
      private UUID departmentId;

      @Column(name = "station_id")
      private UUID stationId;

      @Column(name = "job_title", length = 100)
      private String jobTitle;

      @Enumerated(EnumType.STRING)
      @Column(name = "user_context", nullable = false, length = 50)
      private UserContext userContext = UserContext.INTERNAL;

      @Type(JsonBinaryType.class)
      @Column(name = "functions", columnDefinition = "text[]")
      private List<String> functions;
  }
  ```

**⚠️ Principles:**

- UUID types (not String)
- Nullable company_id for system users
- Default userContext = INTERNAL
- @Enumerated(EnumType.STRING) for readability
- Index on frequently queried fields

**Checklist:**

- [ ] Migration script created
- [ ] Rollback script created (`V3_1__rollback_policy_fields.sql`)
- [ ] Entity updated with new fields
- [ ] Lombok annotations correct
- [ ] No breaking changes to existing queries
- [ ] Test migration on local DB

**Estimated Time:** 3 hours

---

#### 🗄️ 1.3.2 - Company Table Extensions

**Location:** `services/company-service/src/main/resources/db/migration/`

- [ ] **Create `V3__add_policy_fields_to_companies.sql`**

  ```sql
  -- Add relationship fields
  ALTER TABLE companies
  ADD COLUMN parent_company_id UUID,
  ADD COLUMN relationship_type VARCHAR(50);

  -- Add foreign key (self-referencing)
  ALTER TABLE companies
  ADD CONSTRAINT fk_companies_parent
      FOREIGN KEY (parent_company_id) REFERENCES companies(id)
      ON DELETE SET NULL;

  -- Create index
  CREATE INDEX idx_companies_parent ON companies(parent_company_id);
  CREATE INDEX idx_companies_type ON companies(type);

  -- Update existing type enum (if needed)
  ALTER TABLE companies
  ADD CONSTRAINT chk_company_type
      CHECK (type IN ('MANUFACTURER', 'CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR'));
  ```

- [ ] **Update `Company.java` entity**

  ```java
  @Entity
  @Table(name = "companies")
  public class Company extends BaseEntity {

      @Enumerated(EnumType.STRING)
      @Column(name = "type", nullable = false, length = 30)
      private CompanyType type;

      // 🔥 NEW FIELDS
      @Column(name = "parent_company_id")
      private UUID parentCompanyId;

      @Column(name = "relationship_type", length = 50)
      private String relationshipType;

      // Business method
      public boolean isExternal() {
          return !CompanyType.INTERNAL.equals(this.type);
      }
  }
  ```

**Checklist:**

- [ ] Migration script created
- [ ] Entity updated
- [ ] Existing CompanyType enum aligned
- [ ] Test migration

**Estimated Time:** 2 hours

---

#### 🗄️ 1.3.3 - New Tables (Day 3-4)

- [ ] **Create `V4__create_departments_table.sql`**

  ```sql
  CREATE TABLE departments (
      id UUID PRIMARY KEY,
      company_id UUID NOT NULL,
      code VARCHAR(50) NOT NULL,
      name VARCHAR(200) NOT NULL,
      name_en VARCHAR(200),
      type VARCHAR(50) NOT NULL,
      manager_id UUID,
      active BOOLEAN DEFAULT true,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      created_by VARCHAR(100),
      updated_by VARCHAR(100),
      version BIGINT DEFAULT 0,
      deleted BOOLEAN DEFAULT false,

      CONSTRAINT fk_departments_company
          FOREIGN KEY (company_id) REFERENCES companies(id)
          ON DELETE CASCADE,

      CONSTRAINT fk_departments_manager
          FOREIGN KEY (manager_id) REFERENCES users(id)
          ON DELETE SET NULL,

      CONSTRAINT uk_departments_company_code
          UNIQUE (company_id, code),

      CONSTRAINT chk_department_type
          CHECK (type IN ('PRODUCTION', 'QUALITY', 'WAREHOUSE', 'FINANCE',
                         'SALES', 'PURCHASING', 'HR', 'IT', 'MANAGEMENT'))
  );

  CREATE INDEX idx_departments_company ON departments(company_id);
  CREATE INDEX idx_departments_type ON departments(type);
  ```

- [ ] **Create `V5__create_company_relationships_table.sql`**

  ```sql
  CREATE TABLE company_relationships (
      id UUID PRIMARY KEY,
      source_company_id UUID NOT NULL,
      target_company_id UUID NOT NULL,
      relationship_type VARCHAR(50) NOT NULL,
      status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
      allowed_modules TEXT[],
      allowed_actions TEXT[],
      start_date TIMESTAMP,
      end_date TIMESTAMP,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      created_by VARCHAR(100),
      updated_by VARCHAR(100),
      version BIGINT DEFAULT 0,

      CONSTRAINT fk_relationships_source
          FOREIGN KEY (source_company_id) REFERENCES companies(id)
          ON DELETE CASCADE,

      CONSTRAINT fk_relationships_target
          FOREIGN KEY (target_company_id) REFERENCES companies(id)
          ON DELETE CASCADE,

      CONSTRAINT uk_relationships_pair
          UNIQUE (source_company_id, target_company_id),

      CONSTRAINT chk_relationship_type
          CHECK (relationship_type IN ('CUSTOMER', 'SUPPLIER', 'SUBCONTRACTOR')),

      CONSTRAINT chk_relationship_status
          CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED'))
  );

  CREATE INDEX idx_relationships_source ON company_relationships(source_company_id);
  CREATE INDEX idx_relationships_target ON company_relationships(target_company_id);
  ```

- [ ] **Create `V6__create_user_permissions_table.sql`**

  ```sql
  CREATE TABLE user_permissions (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL,
      endpoint VARCHAR(200) NOT NULL,
      operation VARCHAR(50) NOT NULL,
      scope VARCHAR(50) NOT NULL,
      permission_type VARCHAR(20) NOT NULL,
      valid_from TIMESTAMP,
      valid_until TIMESTAMP,
      granted_by UUID,
      reason TEXT,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      version BIGINT DEFAULT 0,

      CONSTRAINT fk_permissions_user
          FOREIGN KEY (user_id) REFERENCES users(id)
          ON DELETE CASCADE,

      CONSTRAINT fk_permissions_granted_by
          FOREIGN KEY (granted_by) REFERENCES users(id)
          ON DELETE SET NULL,

      CONSTRAINT chk_permission_operation
          CHECK (operation IN ('READ', 'WRITE', 'DELETE', 'APPROVE', 'EXPORT', 'MANAGE')),

      CONSTRAINT chk_permission_scope
          CHECK (scope IN ('SELF', 'COMPANY', 'CROSS_COMPANY', 'GLOBAL')),

      CONSTRAINT chk_permission_type
          CHECK (permission_type IN ('ALLOW', 'DENY'))
  );

  CREATE INDEX idx_permissions_user_endpoint ON user_permissions(user_id, endpoint);
  CREATE INDEX idx_permissions_user ON user_permissions(user_id);
  CREATE INDEX idx_permissions_valid_until ON user_permissions(valid_until);
  ```

- [ ] **Create `V7__create_policy_decisions_audit_table.sql`**

  ```sql
  CREATE TABLE policy_decisions_audit (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL,
      company_id UUID,
      company_type VARCHAR(50),
      endpoint VARCHAR(200) NOT NULL,
      operation VARCHAR(50) NOT NULL,
      scope VARCHAR(50),
      decision VARCHAR(10) NOT NULL,
      reason TEXT NOT NULL,
      policy_version VARCHAR(20),
      request_ip VARCHAR(50),
      request_id VARCHAR(100),
      latency_ms INTEGER,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

      CONSTRAINT chk_audit_decision
          CHECK (decision IN ('ALLOW', 'DENY'))
  );

  -- Partitioning by month for performance (optional, advanced)
  -- CREATE INDEX idx_audit_created_at ON policy_decisions_audit(created_at);

  CREATE INDEX idx_audit_user_decision ON policy_decisions_audit(user_id, decision);
  CREATE INDEX idx_audit_endpoint ON policy_decisions_audit(endpoint);
  CREATE INDEX idx_audit_created_at ON policy_decisions_audit(created_at);

  -- 💡 FUTURE: Monthly partitioning for performance
  -- This table will grow fast. Plan partitioning strategy early.
  -- Example: CREATE TABLE policy_decisions_audit_2025_10 PARTITION OF policy_decisions_audit
  --          FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
  ```

  **⚠️ Performance Note:**

  - This table will grow rapidly (every request = 1 row)
  - Consider monthly partitioning from start
  - Use TimescaleDB or PostgreSQL native partitioning
  - Archive old data (>6 months) to cold storage

- [ ] **Create `V8__create_policy_registry_table.sql`**

  ```sql
  CREATE TABLE policy_registry (
      id UUID PRIMARY KEY,
      endpoint VARCHAR(200) NOT NULL UNIQUE,
      operation VARCHAR(50) NOT NULL,
      scope VARCHAR(50) NOT NULL,
      allowed_company_types TEXT[],
      default_roles TEXT[],
      requires_grant BOOLEAN DEFAULT false,
      platform_policy JSONB,
      description TEXT,
      active BOOLEAN DEFAULT true,
      version VARCHAR(20) DEFAULT 'v1',
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      created_by VARCHAR(100),
      updated_by VARCHAR(100)
  );

  CREATE INDEX idx_registry_endpoint ON policy_registry(endpoint);
  CREATE INDEX idx_registry_active ON policy_registry(active);
  ```

**⚠️ Critical Principles:**

- All tables have UUID primary keys
- All tables have audit fields (created_at, updated_at, version)
- All tables have proper indexes
- All tables have check constraints
- Rollback scripts for each migration

**Checklist:**

- [ ] All 5 new tables created
- [ ] Indexes optimized for queries
- [ ] Foreign keys properly set
- [ ] Check constraints enforce data integrity
- [ ] Rollback scripts created
- [ ] Test migrations locally

**Estimated Time:** 6 hours

---

### 📦 1.4 - Create Domain Entities (Day 4-5)

#### ✅ Tasks:

- [ ] **Create `Department.java` entity**

  - Location: `services/company-service/src/main/java/com/fabricmanagement/company/domain/aggregate/`
  - Extends `BaseEntity`
  - Maps to `departments` table
  - Business methods: `activate()`, `deactivate()`, `assignManager()`

- [ ] **Create `CompanyRelationship.java` entity**

  - Location: `services/company-service/src/main/java/com/fabricmanagement/company/domain/aggregate/`
  - Extends `BaseEntity`
  - Business methods: `activate()`, `suspend()`, `terminate()`

- [ ] **Create `UserPermission.java` entity**

  - Location: `shared/shared-domain/src/main/java/com/fabricmanagement/shared/domain/policy/`
  - Extends `BaseEntity`
  - Business methods: `isExpired()`, `isActive()`, `grant()`, `revoke()`

- [ ] **Create `PolicyDecisionAudit.java` entity**

  - Location: `shared/shared-domain/src/main/java/com/fabricmanagement/shared/domain/policy/`
  - Immutable entity (no setters after creation)

- [ ] **Create `PolicyRegistry.java` entity**
  - Location: `shared/shared-domain/src/main/java/com/fabricmanagement/shared/domain/policy/`
  - Business methods: `activate()`, `deactivate()`, `updateVersion()`

**Checklist:**

- [ ] Each entity extends `BaseEntity`
- [ ] UUID type safety enforced
- [ ] Rich domain model (behavior, not anemic)
- [ ] JavaDoc for public methods
- [ ] Unit tests for business logic

**Estimated Time:** 4 hours

---

### 📦 1.5 - Update JWT Token Generation (Day 5)

**Location:** `shared/shared-security/src/main/java/com/fabricmanagement/shared/security/jwt/`

#### ✅ Tasks:

- [ ] **Update `JwtTokenProvider.java`**

  ```java
  public String generateToken(String username, String tenantId, Map<String, Object> claims) {
      Map<String, Object> tokenClaims = new HashMap<>(claims);

      // 🔥 EXISTING
      tokenClaims.put("tenantId", tenantId);
      tokenClaims.put("role", claims.get("role"));

      // 🔥 NEW CLAIMS
      tokenClaims.put("companyId", claims.get("companyId"));
      tokenClaims.put("companyType", claims.get("companyType"));
      tokenClaims.put("departmentId", claims.get("departmentId"));
      tokenClaims.put("jobTitle", claims.get("jobTitle"));
      tokenClaims.put("permissions", claims.get("permissions"));
      tokenClaims.put("defaultScope", claims.get("defaultScope"));

      return createToken(tokenClaims, username, jwtExpiration);
  }
  ```

- [ ] **Update `AuthService.java` in user-service**
  - Add company/department/permission data to JWT claims
  - Fetch permissions during login

**Checklist:**

- [ ] Backward compatible (existing claims work)
- [ ] New claims are optional
- [ ] Token size < 8KB (browser header limit)
- [ ] Unit tests updated

**Estimated Time:** 2 hours

---

### 📦 1.6 - Update SecurityContextResolver (Day 5)

**Location:** `shared/shared-infrastructure/src/main/java/com/fabricmanagement/shared/infrastructure/resolver/`

#### ✅ Tasks:

- [ ] **Update `SecurityContextResolver.java`**

  ```java
  @Component
  public class SecurityContextResolver implements HandlerMethodArgumentResolver {

      @Override
      public SecurityContext resolveArgument(...) {
          Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

          if (authentication == null) {
              throw new UnauthorizedException("Authentication required");
          }

          JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
          Map<String, Object> claims = jwtAuth.getTokenAttributes();

          return SecurityContext.builder()
              // 🔥 EXISTING
              .tenantId(UUID.fromString(claims.get("tenantId").toString()))
              .userId(authentication.getName())
              .roles(extractRoles(authentication))

              // 🔥 NEW
              .companyId(extractUUID(claims, "companyId"))
              .companyType(extractEnum(claims, "companyType", CompanyType.class))
              .departmentId(extractUUID(claims, "departmentId"))
              .jobTitle(extractString(claims, "jobTitle"))
              .permissions(extractList(claims, "permissions"))
              .defaultScope(extractEnum(claims, "defaultScope", DataScope.class))
              .build();
      }

      private UUID extractUUID(Map<String, Object> claims, String key) {
          Object value = claims.get(key);
          return value != null ? UUID.fromString(value.toString()) : null;
      }

      private <E extends Enum<E>> E extractEnum(Map<String, Object> claims, String key, Class<E> enumClass) {
          Object value = claims.get(key);
          if (value == null) return null;

          try {
              return Enum.valueOf(enumClass, value.toString());
          } catch (IllegalArgumentException e) {
              log.warn("Invalid enum value for {}: {}. Using default.", key, value);
              // Return safe default or throw exception based on business rules
              return null;  // Or handle with default enum value
          }
      }

      // ... other helper methods
  }
  ```

  **⚠️ Important:** Invalid enum values should fail gracefully with logging

**Checklist:**

- [ ] Null-safe extraction
- [ ] Type conversion correct (String → UUID)
- [ ] Backward compatible
- [ ] Unit tests

**Estimated Time:** 2 hours

---

### 🎯 Phase 1 Summary

**Deliverables:**

- ✅ 6 new enums (CompanyType, UserContext, DepartmentType, etc.)
- ✅ SecurityContext extended
- ✅ 5 new database tables
- ✅ User/Company entities extended
- ✅ 5 new domain entities
- ✅ JWT token enriched
- ✅ SecurityContext resolver updated

**Total Estimated Time:** 22 hours (~5 days, considering testing & review)

**Success Criteria:**

- [ ] All migrations run successfully
- [ ] No breaking changes to existing code
- [ ] All unit tests pass
- [ ] Code review completed
- [ ] Documentation updated

---

## 💡 Best Practices & Advanced Patterns (Phase 1 Learnings)

### 🏗️ PolicyRegistry JSON Schema Standardization

**Platform Policy JSON Structure:**

```json
{
  "guardrails": {
    "internalWriteOnly": true,
    "requiresTwoStepApproval": false,
    "maxRequestsPerMinute": 100,
    "allowedCompanyTypes": ["INTERNAL"]
  },
  "rules": [
    {
      "condition": "companyType == CUSTOMER && operation == WRITE",
      "action": "DENY",
      "reason": "customers_cannot_write"
    },
    {
      "condition": "scope == GLOBAL && role != SUPER_ADMIN",
      "action": "DENY",
      "reason": "global_scope_admin_only"
    }
  ],
  "metadata": {
    "owner": "security-team",
    "lastReviewed": "2025-10-01",
    "complianceRef": "ISO27001-A.9.4.1"
  }
}
```

**Schema Validation:** Use JSON Schema validator in `PolicyRegistryService`

---

### 🔄 PolicyVersion Auto-Tracking

```java
// PolicyRegistry.java - Add to entity
@PreUpdate
protected void onUpdate() {
    this.version = incrementVersion(this.version);
    this.updatedAt = LocalDateTime.now();

    // Publish version change event
    publishPolicyVersionChangedEvent();
}

private String incrementVersion(String current) {
    // v1.2 → v1.3
    if (current == null) return "v1";

    String[] parts = current.replace("v", "").split("\\.");
    int major = Integer.parseInt(parts[0]);
    int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

    return "v" + major + "." + (minor + 1);
}
```

**Event:**

```java
@Component
public class PolicyRegistryService {
    public void updatePolicy(PolicyRegistry policy) {
        policyRepository.save(policy);  // Triggers @PreUpdate

        // Kafka event for audit
        PolicyVersionChangedEvent event = PolicyVersionChangedEvent.builder()
            .policyId(policy.getId())
            .endpoint(policy.getEndpoint())
            .oldVersion(policy.getVersion())
            .newVersion(policy.getVersion())
            .changedBy(SecurityContextHolder.getCurrentUserId())
            .timestamp(LocalDateTime.now())
            .build();

        kafkaTemplate.send("policy.version.changed", event);
    }
}
```

---

### ⚡ Policy Cache Eviction with Kafka

```java
// Policy update → Cache invalidation
@KafkaListener(topics = "policy.updated", groupId = "policy-cache-group")
public class PolicyCacheEvictionListener {

    @Autowired
    private PolicyCache policyCache;

    public void handlePolicyUpdate(PolicyUpdatedEvent event) {
        log.info("Policy updated, evicting cache for endpoint: {}", event.getEndpoint());

        // Evict from Redis
        policyCache.evict(event.getEndpoint());
        policyCache.evictVersion(event.getVersion());

        // Metrics
        meterRegistry.counter("policy.cache.eviction",
            "endpoint", event.getEndpoint(),
            "reason", "policy_updated"
        ).increment();
    }
}
```

---

### ⏰ UserPermission TTL Cleanup Strategy

```java
// Scheduled job for expired permissions
@Component
@Slf4j
public class UserPermissionCleanupJob {

    @Autowired
    private UserPermissionRepository permissionRepository;

    @Autowired
    private KafkaTemplate<String, PermissionExpiredEvent> kafkaTemplate;

    @Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
    @Transactional
    public void cleanupExpiredPermissions() {
        log.info("Starting expired permissions cleanup");

        LocalDateTime now = LocalDateTime.now();
        List<UserPermission> expired = permissionRepository
            .findByValidUntilBeforeAndStatus(now, "ACTIVE");

        int count = 0;
        for (UserPermission permission : expired) {
            permission.setStatus("EXPIRED");
            permissionRepository.save(permission);

            // Publish event for audit trail
            PermissionExpiredEvent event = PermissionExpiredEvent.builder()
                .permissionId(permission.getId())
                .userId(permission.getUserId())
                .endpoint(permission.getEndpoint())
                .expiredAt(now)
                .build();

            kafkaTemplate.send("permission.expired", event);
            count++;
        }

        log.info("Expired {} permissions. Next cleanup: tomorrow 2 AM", count);

        // Metrics
        meterRegistry.gauge("user.permissions.expired.total", count);
    }
}
```

**Alternative: Event-driven expiry**

```java
// Real-time expiry check during policy evaluation
public PolicyDecision evaluate(PolicyContext ctx) {
    List<UserPermission> grants = getActiveGrants(ctx.getUserId(), ctx.getEndpoint());

    // Filter expired grants (real-time)
    grants = grants.stream()
        .filter(grant -> !grant.isExpired(LocalDateTime.now()))
        .collect(Collectors.toList());

    // ... continue evaluation
}
```

---

### 📊 Metrics & Observability (Production-Ready)

**Key Metrics to Track:**

```java
// 1. PDP Response Time
Timer.builder("policy.decision.latency")
    .tag("endpoint", ctx.getEndpoint())
    .tag("decision", decision.isAllowed() ? "allow" : "deny")
    .tag("company_type", ctx.getCompanyType().name())
    .publishPercentiles(0.95, 0.99)
    .register(meterRegistry);

// 2. Cache Hit/Miss Ratio
meterRegistry.counter("policy.cache.access",
    "result", cacheHit ? "hit" : "miss",
    "endpoint", endpoint
).increment();

// 3. Deny Rate by Reason
meterRegistry.counter("policy.deny.total",
    "reason", decision.getReason(),
    "endpoint", ctx.getEndpoint()
).increment();

// 4. Active User Grants
meterRegistry.gauge("user.permissions.active",
    permissionRepository.countByStatus("ACTIVE"));
```

**Grafana Dashboard Queries:**

```promql
# P95 latency by endpoint
histogram_quantile(0.95,
  rate(policy_decision_latency_bucket[5m])
) by (endpoint)

# Deny rate by reason (top 10)
topk(10,
  rate(policy_deny_total[5m])
) by (reason)

# Cache hit rate
sum(rate(policy_cache_access{result="hit"}[5m])) /
sum(rate(policy_cache_access[5m])) * 100
```

---

## 🔧 Phase 2: Policy Engine (PDP) - Week 2

[Continue with Phase 2 detailed tasks...]

---

**Status Legend:**

- [ ] Not Started
- [🔄] In Progress
- [✅] Completed
- [🔴] Blocked
- [⚠️] Needs Review

**Priority:**

- 🔴 Critical
- 🟡 High
- 🟢 Medium
- 🔵 Low

---

## 🚀 Future Improvements (Optional)

These are NOT required for production, but recommended for scalability:

### 📦 Phase 6: Production Enhancements (Optional)

- [ ] **Redis Cache Integration** (PolicyCache)

  - Replace in-memory cache with Redis
  - Distributed cache for multi-instance deployment
  - Estimated: 4 hours

- [ ] **Kafka Async Audit** (PolicyAuditService)

  - Replace sync logging with Kafka events
  - Non-blocking audit for better performance
  - Estimated: 6 hours

- [ ] **CompanyType Dynamic Lookup**

  - Fetch from Company Service API (not hardcoded INTERNAL)
  - Real-time company type resolution
  - Estimated: 4 hours

- [ ] **CompanyRelationship Trust Check**

  - CROSS_COMPANY scope validation via relationships
  - API call to Company Service
  - Estimated: 6 hours

- [ ] **Metrics & Monitoring**
  - Prometheus metrics for PDP latency
  - Grafana dashboards
  - Estimated: 8 hours

**Total Optional Effort:** ~28 hours (1 week)

---

**Document Version:** 2.0  
**Last Updated:** 2025-10-09 14:52 UTC+1  
**Owner:** Backend Team  
**Status:** ✅ Phase 1-5 Complete, Phase 6 Optional

# 🗄️ PERSISTENCE MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/infrastructure/persistence`  
**Dependencies:** None (Base infrastructure)

---

## 🎯 MODULE PURPOSE

Persistence module, tüm domain entity'lerin base class'larını ve database utility'lerini sağlar.

### **Core Responsibilities**

- ✅ **BaseEntity** - UUID + tenant_id + uid yapısı
- ✅ **AuditableEntity** - createdAt, updatedAt tracking
- ✅ **SpecificationUtils** - Dynamic query building
- ✅ **UIDGenerator** - Human-readable ID generation
- ✅ **SequenceRepository** - Sequence management

---

## 🧱 MODULE STRUCTURE

```
persistence/
├─ BaseEntity.java              # Base class for all entities
├─ AuditableEntity.java         # Auditable base class
├─ SpecificationUtils.java      # Query specification utilities
├─ UIDGenerator.java            # UID generator
└─ SequenceRepository.java      # Sequence management
```

---

## 🔗 DEPENDENCIES

| Dependency          | Type      | Purpose               |
| ------------------- | --------- | --------------------- |
| **Spring Data JPA** | Framework | Entity management     |
| **Hibernate**       | ORM       | Database abstraction  |
| **PostgreSQL**      | Database  | Data storage          |
| **Lombok**          | Library   | Boilerplate reduction |

---

## 📋 KEY FEATURES

### **1. UUID + tenant_id + uid Structure**

Every entity has:

- `id` (UUID) - Machine-level primary key, globally unique
- `tenant_id` (UUID) - Tenant isolation, ensures data segregation
- `uid` (String) - Human-readable reference for audit/debugging

**Purpose:**

- **UUID** → Database integrity, foreign key relationships
- **tenant_id** → Multi-tenant isolation, Row-Level Security (RLS)
- **UID** → Human readability, audit logs, support tickets

### **2. Automatic Tenant Injection**

Tenant ID automatically set from `TenantContext` on entity creation.

```java
@PrePersist
protected void onCreate() {
    if (this.tenantId == null) {
        this.tenantId = TenantContext.getCurrentTenantId();
    }
    // ...
}
```

### **3. Automatic UID Generation**

UID auto-generated using pattern: `{TENANT_UID}-{MODULE}-{ENTITY}-{SEQUENCE}`

**Examples:**

- `ACME-001-USER-00042` - User with sequence 42
- `ACME-001-MAT-05123` - Material with sequence 5123
- `XYZ-002-INV-00891` - Invoice with sequence 891

### **4. Audit Tracking**

Automatic `createdAt` and `updatedAt` timestamps.

```java
@PrePersist
protected void onCreate() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
}

@PreUpdate
protected void onUpdate() {
    this.updatedAt = Instant.now();
}
```

---

## 🎯 USAGE GUIDELINES

### **Entity Definition**

```java
@Entity
@Table(name = "prod_material")
@Getter
@Setter
public class Material extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private MaterialType type;
}
```

### **Repository Definition**

```java
@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByTenantIdAndIsActiveTrue(UUID tenantId);

    Optional<Material> findByTenantIdAndId(UUID tenantId, UUID id);
}
```

---

## ⚠️ CONSTRAINTS

- ❌ NO business logic in BaseEntity
- ❌ NO database queries in BaseEntity
- ❌ NO external dependencies
- ✅ ONLY infrastructure concerns

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

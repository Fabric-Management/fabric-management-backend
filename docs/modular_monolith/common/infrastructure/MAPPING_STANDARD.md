# 🗺️ MAPPING STANDARD

**Version:** 1.0  
**Last Updated:** 2025-10-26  
**Module:** All modules  
**Purpose:** Consistent DTO-Entity mapping across the platform

---

## 🎯 STANDARD: DTO STATIC FROM()

### **Rule:**

**✅ ALWAYS use DTO static from() method**  
**❌ NEVER use MapStruct**  
**❌ NEVER map in Service layer**

---

## 📋 IMPLEMENTATION

### **DTO Structure**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialDto {

    private UUID id;
    private UUID tenantId;
    private String uid;
    private String materialName;
    private MaterialType materialType;
    private BigDecimal unitPrice;
    private Instant createdAt;

    /**
     * Map entity to DTO.
     *
     * <p><b>STANDARD:</b> All DTOs MUST have this method</p>
     */
    public static MaterialDto from(Material entity) {
        return MaterialDto.builder()
            .id(entity.getId())
            .tenantId(entity.getTenantId())
            .uid(entity.getUid())
            .materialName(entity.getMaterialName())
            .materialType(entity.getMaterialType())
            .unitPrice(entity.getUnitPrice())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
```

---

## ✅ BEST PRACTICES

### **1. Service Usage**

```java
// ✅ Good: Clean, one-liner
@Service
public class MaterialService {
    public MaterialDto getMaterial(UUID id) {
        Material material = repository.findById(id).orElseThrow();
        return MaterialDto.from(material);
    }
}

// ❌ Bad: Manual mapping in service
@Service
public class MaterialService {
    public MaterialDto getMaterial(UUID id) {
        Material material = repository.findById(id).orElseThrow();

        MaterialDto dto = new MaterialDto();
        dto.setId(material.getId());
        dto.setName(material.getName());
        // ... 10 more lines

        return dto;
    }
}
```

### **2. List Mapping**

```java
// ✅ Good: Stream + method reference
List<MaterialDto> dtos = materials.stream()
    .map(MaterialDto::from)
    .toList();

// ❌ Bad: Loop
List<MaterialDto> dtos = new ArrayList<>();
for (Material m : materials) {
    dtos.add(MaterialDto.from(m));
}
```

### **3. Optional Mapping**

```java
// ✅ Good: Optional map
Optional<MaterialDto> dto = materialOpt.map(MaterialDto::from);

// ❌ Bad: isPresent check
Optional<MaterialDto> dto;
if (materialOpt.isPresent()) {
    dto = Optional.of(MaterialDto.from(materialOpt.get()));
}
```

---

## 🚫 FORBIDDEN

### **❌ MapStruct**

```java
// ❌ FORBIDDEN: No MapStruct
@Mapper(componentModel = "spring")
public interface MaterialMapper {
    MaterialDto toDto(Material entity);
}
```

**Why:**

- Complexity (extra interface, compile-time generation)
- Overkill for simple mapping
- Less readable
- More files to maintain

### **❌ Manual Mapping in Service**

```java
// ❌ FORBIDDEN: Service should not handle mapping
public MaterialDto create(CreateMaterialRequest request) {
    Material material = materialRepository.save(...);

    // NO! Mapping here!
    MaterialDto dto = new MaterialDto();
    dto.setId(material.getId());
    ...
}
```

**Why:**

- Service gets bloated
- Violates SRP
- Hard to reuse

---

## ✅ ADVANTAGES

**1. Clean Services** ✅

```java
return MaterialDto.from(material);  // One line!
```

**2. Self-Contained** ✅

- DTO knows how to map itself
- No external dependencies

**3. Easy to Test** ✅

```java
@Test
void from_shouldMapCorrectly() {
    Material material = createTestMaterial();
    MaterialDto dto = MaterialDto.from(material);
    assertThat(dto.getId()).isEqualTo(material.getId());
}
```

**4. Consistent** ✅

- Same pattern everywhere
- No surprises

**5. Performance** ✅

- No reflection (MapStruct uses code generation)
- Simple field assignment

---

## 📚 EXAMPLES IN CODEBASE

**Already implemented:**

- ✅ UserDto.from(user)
- ✅ CompanyDto.from(company)
- ✅ SubscriptionDto.from(subscription)
- ✅ PolicyDto.from(policy)
- ✅ AuditLogDto.from(auditLog)

**All production modules:** MUST follow this pattern

---

## 🎯 CHECKLIST

Before committing DTO:

- [ ] Has static from() method?
- [ ] Maps all essential fields?
- [ ] No logic in from() (just field assignment)?
- [ ] Service uses MaterialDto.from()?

---

**Last Updated:** 2025-10-26  
**Maintained By:** Fabric Management Team  
**Status:** ✅ MANDATORY STANDARD

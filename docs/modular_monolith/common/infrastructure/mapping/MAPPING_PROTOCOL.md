# 🔄 MAPPING MODULE PROTOCOL

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/infrastructure/mapping`  
**Dependencies:** persistence

---

## 🎯 MODULE PURPOSE

MapStruct configuration for DTO-Entity transformations.

---

## 📂 STRUCTURE

```
mapping/
└─ MapStructConfig.java
```

---

## 💡 EXAMPLE

```java
@Mapper(componentModel = "spring")
public interface MaterialMapper {
    MaterialDto toDto(Material entity);
    Material toEntity(MaterialDto dto);
}
```

---

**Last Updated:** 2025-01-27

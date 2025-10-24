# 💡 EVENTS MODULE EXAMPLES

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Module:** `common/infrastructure/events`

---

## 📋 DOMAINEVENT EXAMPLE

```java
@Getter
public abstract class DomainEvent {

    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredAt = Instant.now();
    private final UUID tenantId;
    private final String eventType;

    protected DomainEvent(UUID tenantId, String eventType) {
        this.tenantId = tenantId;
        this.eventType = eventType;
    }
}
```

## 📢 CUSTOM EVENT EXAMPLE

```java
@Getter
public class MaterialCreatedEvent extends DomainEvent {

    private final UUID materialId;
    private final String materialName;

    public MaterialCreatedEvent(UUID tenantId, UUID materialId, String materialName) {
        super(tenantId, "MATERIAL_CREATED");
        this.materialId = materialId;
        this.materialName = materialName;
    }
}
```

## 🔄 EVENT PUBLISHER EXAMPLE

```java
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(DomainEvent event) {
        eventPublisher.publishEvent(event);
    }
}
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

# 💬 COMMUNICATION PATTERNS

**Version:** 1.0  
**Last Updated:** 2025-01-27  
**Status:** ✅ Active Development

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Direct Call Pattern (Facade)](#direct-call-pattern-facade)
3. [Event Pattern](#event-pattern)
4. [Outbox Pattern](#outbox-pattern)
5. [CQRS Pattern](#cqrs-pattern)
6. [Request-Response Pattern](#request-response-pattern)
7. [Best Practices](#best-practices)

---

## 🎯 OVERVIEW

Modular Monolith mimarisinde, modüller arası iletişim için **5 farklı pattern** kullanılır. Her pattern'in kendine özgü kullanım senaryoları ve avantajları vardır.

### **Pattern Seçim Matrisi**

| Pattern                  | Senkron/Asenkron | Kullanım Senaryosu                   | Performans    | Karmaşıklık |
| ------------------------ | ---------------- | ------------------------------------ | ------------- | ----------- |
| **Direct Call (Facade)** | Senkron          | Read-only, low-latency               | ⚡ Çok Yüksek | 🟢 Düşük    |
| **Event**                | Asenkron         | Eventual consistency, loose coupling | ⚡ Yüksek     | 🟡 Orta     |
| **Outbox**               | Asenkron         | Reliable event publishing            | ⚡ Orta       | 🟠 Yüksek   |
| **CQRS**                 | Senkron          | Command vs Query separation          | ⚡ Yüksek     | 🟡 Orta     |
| **Request-Response**     | Senkron          | Complex operations                   | ⚡ Orta       | 🟢 Düşük    |

---

## 🔗 DIRECT CALL PATTERN (FACADE)

### **Ne Zaman Kullanılır?**

- ✅ **Read-only** operasyonlar
- ✅ **Low-latency** gereksinimi
- ✅ **Senkron** veri okuma
- ✅ **Cross-module** data access
- ❌ **Write** operasyonları için kullanılmaz

### **Avantajlar**

- ⚡ **Çok Hızlı:** In-process call, network overhead yok
- 🎯 **Basit:** Direct method call
- 🔍 **Debuglanabilir:** Tek JVM içinde
- 🧪 **Test Edilebilir:** Mock facade interface

### **Dezavantajlar**

- ⚠️ **Tight Coupling:** Modüller arası dependency
- ⚠️ **Compile-time Dependency:** Facade interface değişirse compile error

### **Implementation**

#### **1. Facade Interface (Production Module)**

```java
package com.fabricmanagement.production.masterdata.material.api.facade;

import com.fabricmanagement.production.masterdata.material.api.dto.MaterialDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Material Facade - Internal API
 *
 * Provides clean interface for cross-module communication.
 * Other modules should only interact with Material through this facade.
 * This is IN-PROCESS communication (no HTTP overhead).
 */
public interface MaterialFacade {

    /**
     * Get material by ID
     *
     * @param tenantId Tenant ID
     * @param materialId Material ID
     * @return Material DTO if found
     */
    Optional<MaterialDto> findById(UUID tenantId, UUID materialId);

    /**
     * Get all materials by tenant
     *
     * @param tenantId Tenant ID
     * @return List of materials
     */
    List<MaterialDto> findByTenant(UUID tenantId);

    /**
     * Get materials by type
     *
     * @param tenantId Tenant ID
     * @param type Material type
     * @return List of materials
     */
    List<MaterialDto> findByType(UUID tenantId, String type);

    /**
     * Check if material exists
     *
     * @param tenantId Tenant ID
     * @param materialId Material ID
     * @return true if exists
     */
    boolean exists(UUID tenantId, UUID materialId);
}
```

#### **2. Facade Implementation (Production Module)**

```java
package com.fabricmanagement.production.masterdata.material.app;

import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.api.dto.MaterialDto;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.infra.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MaterialService implements MaterialFacade {

    private final MaterialRepository repository;

    @Override
    public Optional<MaterialDto> findById(UUID tenantId, UUID materialId) {
        log.debug("Finding material: tenantId={}, materialId={}", tenantId, materialId);
        return repository.findByTenantIdAndId(tenantId, materialId)
            .map(MaterialDto::from);
    }

    @Override
    public List<MaterialDto> findByTenant(UUID tenantId) {
        log.debug("Finding all materials: tenantId={}", tenantId);
        return repository.findByTenantIdAndIsActiveTrue(tenantId)
            .stream()
            .map(MaterialDto::from)
            .collect(Collectors.toList());
    }

    @Override
    public List<MaterialDto> findByType(UUID tenantId, String type) {
        log.debug("Finding materials by type: tenantId={}, type={}", tenantId, type);
        return repository.findByTenantIdAndType(tenantId, type)
            .stream()
            .map(MaterialDto::from)
            .collect(Collectors.toList());
    }

    @Override
    public boolean exists(UUID tenantId, UUID materialId) {
        log.debug("Checking material existence: tenantId={}, materialId={}", tenantId, materialId);
        return repository.existsByTenantIdAndId(tenantId, materialId);
    }
}
```

#### **3. Consumer (Logistics Module)**

```java
package com.fabricmanagement.logistics.inventory.app;

import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.api.dto.MaterialDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final MaterialFacade materialFacade; // Direct dependency on facade

    public void checkMaterialAvailability(UUID tenantId, UUID materialId) {
        log.info("Checking material availability: materialId={}", materialId);

        // Direct in-process call - Very fast!
        Optional<MaterialDto> material = materialFacade.findById(tenantId, materialId);

        if (material.isEmpty()) {
            throw new MaterialNotFoundException(materialId);
        }

        // Use material data
        log.info("Material found: {}", material.get().getName());
    }
}
```

---

## 📢 EVENT PATTERN

### **Ne Zaman Kullanılır?**

- ✅ **Asenkron** iletişim
- ✅ **Eventual consistency** kabul edilebilir
- ✅ **Loose coupling** gerekli
- ✅ **Multiple consumers** var
- ❌ **Immediate consistency** gerekli değil

### **Avantajlar**

- 🔓 **Loose Coupling:** Modüller birbirinden bağımsız
- 📢 **Multiple Consumers:** Birden fazla modül aynı event'i dinleyebilir
- ⚡ **Non-blocking:** Publisher event'i publish ettikten sonra devam eder
- 🧪 **Test Edilebilir:** Event listener'lar izole test edilebilir

### **Dezavantajlar**

- ⚠️ **Eventual Consistency:** Immediate consistency yok
- ⚠️ **Debugging Zorluğu:** Event flow takibi zor olabilir
- ⚠️ **Event Versioning:** Event structure değişirse sorun olabilir

### **Implementation**

#### **1. Domain Event (Production Module)**

```java
package com.fabricmanagement.production.masterdata.material.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MaterialCreatedEvent extends DomainEvent {

    private final UUID materialId;
    private final String materialName;
    private final String materialType;

    public MaterialCreatedEvent(UUID tenantId, UUID materialId, String materialName, String materialType) {
        super(tenantId, "MATERIAL_CREATED");
        this.materialId = materialId;
        this.materialName = materialName;
        this.materialType = materialType;
    }
}
```

#### **2. Event Publisher (Production Module)**

```java
package com.fabricmanagement.production.masterdata.material.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import com.fabricmanagement.production.masterdata.material.domain.event.MaterialCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MaterialService {

    private final MaterialRepository repository;
    private final DomainEventPublisher eventPublisher;

    public MaterialDto createMaterial(CreateMaterialRequest request) {
        log.info("Creating material: {}", request.getName());

        // 1. Save material
        Material material = Material.create(request.getName(), request.getType());
        Material saved = repository.save(material);

        // 2. Publish event
        eventPublisher.publish(new MaterialCreatedEvent(
            TenantContext.getCurrentTenantId(),
            saved.getId(),
            saved.getName(),
            saved.getType().toString()
        ));

        log.info("Material created and event published: materialId={}", saved.getId());

        return MaterialDto.from(saved);
    }
}
```

#### **3. Event Listener (Logistics Module)**

```java
package com.fabricmanagement.logistics.inventory.app;

import com.fabricmanagement.production.masterdata.material.domain.event.MaterialCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialEventListener {

    private final InventoryService inventoryService;

    @EventListener
    @Transactional
    public void handleMaterialCreated(MaterialCreatedEvent event) {
        log.info("Handling MaterialCreatedEvent: materialId={}, materialName={}",
            event.getMaterialId(), event.getMaterialName());

        // Create inventory item for new material
        inventoryService.createInventoryForMaterial(
            event.getTenantId(),
            event.getMaterialId(),
            event.getMaterialName()
        );

        log.info("Inventory created for material: materialId={}", event.getMaterialId());
    }
}
```

---

## 📦 OUTBOX PATTERN

### **Ne Zaman Kullanılır?**

- ✅ **Reliable event publishing** gerekli
- ✅ **Transaction safety** önemli
- ✅ **At-least-once delivery** garantisi
- ✅ **Kafka** veya benzeri message broker kullanılıyor
- ❌ **Simple in-process events** yeterli değil

### **Avantajlar**

- 🔒 **Transaction Safety:** Event ve data aynı transaction'da
- 🔄 **Guaranteed Delivery:** Event mutlaka deliver edilir
- 🛡️ **Resilience:** Message broker down olsa bile event kaydedilir
- 📊 **Audit Trail:** Tüm event'ler database'de

### **Dezavantajlar**

- ⚠️ **Complexity:** Outbox table + scheduler gerekli
- ⚠️ **Storage:** Event'ler database'de saklanır
- ⚠️ **Latency:** Event delivery scheduler'a bağlı

### **Implementation**

#### **1. Outbox Entity**

```java
package com.fabricmanagement.common.infrastructure.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    @Column
    private Integer retryCount;

    public static OutboxEvent from(DomainEvent event) {
        return OutboxEvent.builder()
            .tenantId(event.getTenantId())
            .eventType(event.getEventType())
            .payload(JsonUtils.toJson(event))
            .status(OutboxEventStatus.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
    }
}
```

#### **2. Outbox Publisher**

```java
package com.fabricmanagement.production.masterdata.material.app;

import com.fabricmanagement.common.infrastructure.events.OutboxEvent;
import com.fabricmanagement.common.infrastructure.events.OutboxEventRepository;
import com.fabricmanagement.production.masterdata.material.domain.event.MaterialCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final OutboxEventRepository outboxRepository;

    public MaterialDto createMaterial(CreateMaterialRequest request) {
        log.info("Creating material: {}", request.getName());

        // 1. Save material
        Material material = Material.create(request.getName(), request.getType());
        Material saved = materialRepository.save(material);

        // 2. Save event to outbox (same transaction!)
        MaterialCreatedEvent event = new MaterialCreatedEvent(
            TenantContext.getCurrentTenantId(),
            saved.getId(),
            saved.getName(),
            saved.getType().toString()
        );
        outboxRepository.save(OutboxEvent.from(event));

        log.info("Material and outbox event saved: materialId={}", saved.getId());

        return MaterialDto.from(saved);
    }
}
```

#### **3. Outbox Scheduler**

```java
package com.fabricmanagement.common.infrastructure.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = repository.findByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        log.debug("Publishing {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                // Publish to Kafka
                kafkaTemplate.send("domain-events", event.getEventType(), event.getPayload());

                // Mark as published
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                repository.save(event);

                log.info("Event published: eventId={}, eventType={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to publish event: eventId={}", event.getId(), e);

                // Retry logic
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() > 3) {
                    event.setStatus(OutboxEventStatus.FAILED);
                }
                repository.save(event);
            }
        }
    }
}
```

---

## 🎯 CQRS PATTERN

### **Ne Zaman Kullanılır?**

- ✅ **Command** (write) vs **Query** (read) ayrımı
- ✅ **Complex business logic**
- ✅ **Different read/write models**
- ✅ **Performance optimization**
- ❌ **Simple CRUD** operations

### **Implementation**

#### **1. Command**

```java
package com.fabricmanagement.production.masterdata.material.app.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CreateMaterialCommand {
    private String name;
    private String type;
    private String category;
}
```

#### **2. Command Handler**

```java
package com.fabricmanagement.production.masterdata.material.app.command;

import com.fabricmanagement.common.infrastructure.cqrs.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateMaterialCommandHandler implements CommandHandler<CreateMaterialCommand> {

    private final MaterialRepository repository;
    private final DomainEventPublisher eventPublisher;

    @Override
    public void handle(CreateMaterialCommand command) {
        // Business logic
        Material material = Material.create(command.getName(), command.getType());
        repository.save(material);

        // Publish event
        eventPublisher.publish(new MaterialCreatedEvent(material.getId()));
    }
}
```

#### **3. Query**

```java
package com.fabricmanagement.production.masterdata.material.app.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class GetMaterialQuery {
    private UUID materialId;
}
```

#### **4. Query Handler**

```java
package com.fabricmanagement.production.masterdata.material.app.query;

import com.fabricmanagement.common.infrastructure.cqrs.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMaterialQueryHandler implements QueryHandler<GetMaterialQuery, MaterialDto> {

    private final MaterialRepository repository;

    @Override
    public MaterialDto handle(GetMaterialQuery query) {
        Material material = repository.findById(query.getMaterialId())
            .orElseThrow(() -> new MaterialNotFoundException(query.getMaterialId()));

        return MaterialDto.from(material);
    }
}
```

---

## 📋 REQUEST-RESPONSE PATTERN

### **Ne Zaman Kullanılır?**

- ✅ **Complex operations** with return value
- ✅ **Senkron** communication
- ✅ **Direct response** gerekli
- ❌ **Simple read operations** (use facade instead)

### **Implementation**

```java
// Request
public record CalculateMaterialCostRequest(UUID materialId, Double quantity) {}

// Response
public record CalculateMaterialCostResponse(Double totalCost, String currency) {}

// Service
public CalculateMaterialCostResponse calculateCost(CalculateMaterialCostRequest request) {
    Material material = repository.findById(request.materialId())
        .orElseThrow(() -> new MaterialNotFoundException(request.materialId()));

    Double totalCost = material.getUnitCost() * request.quantity();

    return new CalculateMaterialCostResponse(totalCost, "USD");
}
```

---

## ✅ BEST PRACTICES

### **1. Pattern Seçimi**

| Senaryo                     | Kullan                  | Kullanma       |
| --------------------------- | ----------------------- | -------------- |
| **Read-only data access**   | Facade                  | Event          |
| **Asenkron notification**   | Event                   | Facade         |
| **Reliable event delivery** | Outbox                  | Simple Event   |
| **Complex business logic**  | CQRS                    | Simple Service |
| **Immediate response**      | Facade/Request-Response | Event          |

### **2. Error Handling**

```java
// ✅ Good: Handle errors gracefully
try {
    MaterialDto material = materialFacade.findById(tenantId, materialId)
        .orElseThrow(() -> new MaterialNotFoundException(materialId));
} catch (MaterialNotFoundException e) {
    log.warn("Material not found: {}", materialId);
    // Handle error
}

// ❌ Bad: Let errors propagate
MaterialDto material = materialFacade.findById(tenantId, materialId).get(); // Can throw NoSuchElementException
```

### **3. Logging**

```java
// ✅ Good: Log all cross-module calls
log.info("Calling MaterialFacade.findById: tenantId={}, materialId={}", tenantId, materialId);
MaterialDto material = materialFacade.findById(tenantId, materialId);
log.info("MaterialFacade.findById completed: found={}", material.isPresent());

// ❌ Bad: No logging
MaterialDto material = materialFacade.findById(tenantId, materialId);
```

### **4. Testing**

```java
// ✅ Good: Mock facade in tests
@Mock
private MaterialFacade materialFacade;

@Test
void test() {
    when(materialFacade.findById(any(), any())).thenReturn(Optional.of(new MaterialDto()));
    // Test
}

// ❌ Bad: Direct dependency on implementation
@Autowired
private MaterialService materialService; // This is implementation, not facade!
```

---

**Last Updated:** 2025-01-27  
**Maintained By:** Fabric Management Team

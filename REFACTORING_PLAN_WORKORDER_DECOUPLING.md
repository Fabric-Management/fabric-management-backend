# WorkOrder Cross-Module Decoupling — Refactoring Plan

> **Tarih:** 2026-03-30
> **Durum:** Planlama
> **Scope:** `production/execution/workorder`, `sales/salesorder`, `approval`, `platform/user`
> **Pattern:** Port/Adapter + Anti-Corruption Layer (ACL)

---

## 1. Mevcut Durum — Coupling Haritası

### Akış Özeti

```
SalesOrder.confirm()
  ├─ [SYNC]  SalesOrderRuleEngine → WorkOrderService.createWorkOrder()  → DRAFT WO
  ├─ [SYNC]  SalesOrderService publishes SalesOrderConfirmedEvent
  └─ [ASYNC] WorkOrderSalesEventListener → WorkOrderService.createFromSalesOrderLine()
               ├─ Mevcut DRAFT varsa: promote
               ├─ Yoksa: yeni WO oluştur
               └─ ApprovalGuardService.checkAndEnforceApproval() → PENDING_APPROVAL | APPROVED
```

### Tespit Edilen Coupling Noktaları (5)

| # | Kaynak → Hedef | Tür | Şiddet | Neden Sorun |
|---|----------------|-----|--------|-------------|
| C1 | `SalesOrderRuleEngine` → `WorkOrderService` | Cross-BC service | 🔴 Yüksek | `sales` doğrudan `production.workorder.app` import ediyor |
| C2 | `WorkOrderService.createFromSalesOrderLine(SalesOrderLineSnapshot)` | Cross-BC type leak | 🟡 Orta | Servis metodu `sales` event iç tipine bağımlı |
| C3 | `WorkOrderService` → `ApprovalGuardService` | Cross-BC service | 🟡 Orta | `production` doğrudan `approval.app` import ediyor |
| C4 | `ApprovalGuardService` → `UserRepository` | Infra bypass | 🔴 Yüksek | `approval` modülü `platform.user.infra.repository` erişiyor |
| C5 | `WorkOrderService` → `TradingPartnerCertificationService` | Platform service | 🟢 Düşük | Platform → Domain yönü AGENTS.md'e göre kabul edilebilir |

### Coupling Diyagramı (Mevcut)

```
                    ┌──────────┐
                    │  sales/  │
                    │salesorder│
                    └────┬─────┘
                         │ C1: doğrudan WorkOrderService import
                         ▼
┌──────────┐    C3  ┌──────────┐    C2: SalesOrderLineSnapshot
│ approval │◄───────│production│    tip sızması
│          │        │/workorder│
└────┬─────┘        └────┬─────┘
     │ C4                │ C5 (düşük)
     ▼                   ▼
┌──────────┐        ┌──────────┐
│platform/ │        │platform/ │
│user/infra│        │trading   │
│/repo     │        │partner   │
└──────────┘        └──────────┘
```

---

## 2. Hedef Mimari

### Tasarım Kararları

**Karar 1 — C5 (`TradingPartnerCertificationService`) dokunulmayacak.**
Platform servislerine domain modülden erişim AGENTS.md'e göre kabul edilebilir. Ayrıca `applySupplierSnapshot()` best-effort (try/catch ile sarılmış) ve iş akışının kritik olmayan bir parçası. Gereksiz soyutlama yaratmıyoruz.

**Karar 2 — Dual-path (sync + async) tasarım korunacak.**
`SalesOrderRuleEngine`'in senkron DRAFT oluşturması + event listener'ın asenkron approval yönetimi bilinçli bir tasarım. İş emri, sipariş onay transaction'ı içinde garanti altına alınmalı. Bu akış değişmeyecek — sadece coupling yöntemi değişecek.

**Karar 3 — Port/Adapter her iki yönde uygulanacak.**
- `sales` → `production`: **`ProductionOrderPort`** (sales'in kendi dilinde)
- `production` → `approval`: **`ApprovalPort`** (production'ın kendi dilinde)
- `approval` → `platform/user`: **`UserTrustLevelPort`** (approval'ın kendi dilinde)

### Hedef Coupling Diyagramı

```
                    ┌──────────┐
                    │  sales/  │ ProductionOrderPort (interface, sales domain)
                    │salesorder│──────────┐
                    └──────────┘          │ implement eder
                                          ▼
                                   ┌──────────────────┐
                                   │ WorkOrderCreation │ (production/workorder/app/adapter)
                                   │ Adapter           │
                                   └────────┬──────────┘
                                            │
                                            ▼
┌──────────┐ ApprovalPort       ┌──────────────────┐
│ approval │◄── (interface,  ───│ WorkOrderService  │
│          │    production       └────────┬──────────┘
└────┬─────┘    domain)                  │ C5 korunur (kabul edilebilir)
     │                                    ▼
     │ UserTrustLevelPort         ┌──────────────┐
     │ (interface, approval       │platform/     │
     │  domain)                   │tradingpartner│
     ▼                            └──────────────┘
┌──────────────────┐
│UserTrustLevel    │ (platform/user/app/adapter)
│Adapter           │
└──────────────────┘
```

**Sonuç:** Her bounded context yalnızca kendi tanımladığı interface'leri bilir. Hiçbir modül başka modülün `app/` service'ini veya `infra/` repository'sini doğrudan import etmez.

---

## 3. Uygulama Planı

### Phase 1 — Quick Wins (Kolay, yüksek değer)

#### Unit 1.1: SalesOrderLineSnapshot tip sızmasını gider (C2)

**Problem:** `WorkOrderService.createFromSalesOrderLine()` parametresi `SalesOrderConfirmedEvent.SalesOrderLineSnapshot` tipinde — production servisi sales event tipini biliyor.

**Çözüm:** Production modülünde lokal bir record tanımla; listener'da mapping yap.

**Yeni dosya:** `production/execution/workorder/dto/IncomingSalesOrderLine.java`
```java
public record IncomingSalesOrderLine(
    UUID lineId,
    String productCode,
    BigDecimal quantity,
    String unit,
    LocalDate requestedDeliveryDate
) {}
```

**Değişen dosyalar:**
| Dosya | Değişiklik |
|-------|-----------|
| `WorkOrderService.createFromSalesOrderLine()` | Parametre tipi `SalesOrderLineSnapshot` → `IncomingSalesOrderLine` |
| `WorkOrderSalesEventListener.onSalesOrderConfirmed()` | Event line'ları `IncomingSalesOrderLine`'a map eder |

**Etki:** `WorkOrderService`'ten `sales` import'u tamamen kalkar.

---

#### Unit 1.2: ApprovalGuardService → UserRepository infra bypass'ı gider (C4)

**Problem:** `ApprovalGuardService` doğrudan `platform.user.infra.repository.UserRepository` import ederek `User.trustLevel` okuyor. Bu, approval modülünden platform/user altyapısına doğrudan erişim.

**Çözüm:** Approval modülünde port tanımla, platform/user'da adapter uygula.

**Yeni dosya:** `approval/domain/port/UserTrustLevelPort.java`
```java
public interface UserTrustLevelPort {
    UserTrustLevel resolveTrustLevel(UUID tenantId, UUID userId);
}
```

**Yeni dosya:** `platform/user/app/adapter/UserTrustLevelAdapter.java`
```java
@Component
public class UserTrustLevelAdapter implements UserTrustLevelPort {
    private final UserRepository userRepository;
    // findByTenantIdAndId → user.getTrustLevel()
    // SystemUser.ID kontrolü burada veya ApprovalGuardService'te kalabilir
}
```

**Değişen dosya:** `ApprovalGuardService`
- `UserRepository userRepo` → `UserTrustLevelPort userTrustLevelPort`
- `User` entity import'u kalkar
- `userRepo.findByTenantIdAndId()` → `userTrustLevelPort.resolveTrustLevel()`

**Tasarım notu:** `SystemUser.ID` kontrolü `ApprovalGuardService`'te kalabilir (zaten `SystemUser.ID` bir sabit, platform.user.domain'den import). Alternatif: adapter'a taşı, ama bu durumda adapter `SystemUser` bilgisini bilmeli — bu sorun değil çünkü adapter zaten platform/user'da.

---

### Phase 2 — Cross-BC Port/Adapter (Orta zorluk, yüksek mimari değer)

#### Unit 2.1: SalesOrderRuleEngine → WorkOrderService bağımlılığını kes (C1)

**Problem:** `SalesOrderRuleEngine` doğrudan `production.execution.workorder.app.WorkOrderService` ve `production.execution.workorder.dto.CreateWorkOrderRequest` import ediyor.

**Çözüm:** Sales modülünde port + command tanımla; production modülünde adapter uygula.

**Tasarım prensibi — Anti-Corruption Layer:**
Sales modülü kendi ubiquitous language'ında konuşur ("production order request"), production'ın dilini ("work order") bilmez.

**Yeni dosya:** `sales/salesorder/domain/port/ProductionOrderPort.java`
```java
/**
 * Sales modülünün üretim talebi oluşturmak için kullandığı port.
 * Sales "work order" terimini bilmez — kendi dilinde "production order" der.
 */
public interface ProductionOrderPort {
    void requestDraftProductionOrder(DraftProductionOrderCommand command);
}
```

**Yeni dosya:** `sales/salesorder/dto/DraftProductionOrderCommand.java`
```java
public record DraftProductionOrderCommand(
    UUID recipeId,
    UUID tradingPartnerId,
    UUID salesOrderLineId,
    BigDecimal plannedQty,
    String unit,
    String currency,
    LocalDate deadline
) {}
```

**Yeni dosya:** `production/execution/workorder/app/adapter/WorkOrderCreationAdapter.java`
```java
@Component
public class WorkOrderCreationAdapter implements ProductionOrderPort {
    private final WorkOrderService workOrderService;
    // DraftProductionOrderCommand → CreateWorkOrderRequest mapping
    // workOrderService.createWorkOrder(request) çağrısı
}
```

**Değişen dosya:** `SalesOrderRuleEngine`
- `WorkOrderService` → `ProductionOrderPort`
- `CreateWorkOrderRequest` → `DraftProductionOrderCommand`
- Tüm `production.execution.workorder` import'ları kalkar

---

#### Unit 2.2: WorkOrderService → ApprovalGuardService bağımlılığını kes (C3)

**Problem:** `WorkOrderService` doğrudan `approval.app.ApprovalGuardService` ve `approval.domain.ApprovalEntityType` import ediyor.

**Çözüm:** Production modülünde port tanımla; approval modülünde adapter uygula.

**Yeni dosya:** `production/execution/workorder/domain/port/ApprovalPort.java`
```java
/**
 * Production modülünün onay mekanizmasına erişim portu.
 * Production "ApprovalEntityType" enum'unu bilmez — entityType'ı String olarak geçirir.
 */
public interface ApprovalPort {
    boolean requiresApproval(
        UUID tenantId, UUID userId, String entityType, UUID entityId, int expiryHours);
}
```

**Yeni dosya:** `approval/app/adapter/ApprovalGuardAdapter.java`
```java
@Component
public class ApprovalGuardAdapter implements ApprovalPort {
    private final ApprovalGuardService approvalGuardService;
    // String entityType → ApprovalEntityType.valueOf(entityType) dönüşümü
    // approvalGuardService.checkAndEnforceApproval() çağrısı
}
```

**Değişen dosya:** `WorkOrderService`
- `ApprovalGuardService` → `ApprovalPort`
- `ApprovalEntityType.WORK_ORDER` → `"WORK_ORDER"` (String sabit)
- Tüm `approval` import'ları kalkar

---

### Phase 3 — ArchUnit Guardrails + Test

#### Unit 3.1: ArchUnit kuralları

**Yeni kural eklenecek:** `ConstitutionArchTest.java`

```java
// Rule X.1: sales must not import production internals
noClasses()
    .that().resideInAPackage("..sales..")
    .should().dependOnClassesThat()
    .resideInAPackage("..production..app..")
    .orShould().dependOnClassesThat()
    .resideInAPackage("..production..infra..")
    .as("sales must communicate with production via ports, not direct service calls");

// Rule X.2: production/workorder must not import approval internals
noClasses()
    .that().resideInAPackage("..production.execution.workorder..")
    .should().dependOnClassesThat()
    .resideInAPackage("..approval..app..")
    .orShould().dependOnClassesThat()
    .resideInAPackage("..approval..domain..")
    .as("production/workorder must use ApprovalPort, not direct approval service");

// Rule X.3: approval must not access platform/user infrastructure
noClasses()
    .that().resideInAPackage("..approval..")
    .should().dependOnClassesThat()
    .resideInAPackage("..platform.user..infra..")
    .as("approval must use UserTrustLevelPort, not UserRepository directly");
```

**Listener istisnaları:** `WorkOrderSalesEventListener`'ın `sales.salesorder.domain.event.SalesOrderConfirmedEvent` import'u **kabul edilir** — event dinlemek, coupling'in kabul edilebilir minimum formudur. ArchUnit kuralı buna izin vermeli (`.should().dependOnClassesThat().resideInAPackage("..sales..domain.event..")` hariç tutulur).

#### Unit 3.2: Unit testler

| Test Dosyası | Mock | Kapsam |
|-------------|------|--------|
| `WorkOrderCreationAdapterTest` | `WorkOrderService` | Command → Request mapping, createWorkOrder delegasyonu |
| `ApprovalGuardAdapterTest` | `ApprovalGuardService` | String entityType → enum mapping, checkAndEnforceApproval delegasyonu |
| `UserTrustLevelAdapterTest` | `UserRepository` | findByTenantIdAndId → trustLevel, kullanıcı bulunamadı |
| `SalesOrderRuleEngineTest` (güncelle) | `ProductionOrderPort` | Port çağrısının doğru command ile yapıldığını doğrula |

---

## 4. Dokunulmayacak Dosyalar — Bilinçli Kararlar

| Dosya | Neden Dokunulmayacak |
|-------|---------------------|
| `WorkOrderService.applySupplierSnapshot()` | Platform → Domain yönü kabul edilebilir (C5). Best-effort, try/catch ile sarılmış. Gereksiz soyutlama yaratılmaz. |
| `WorkOrderApprovalEventListener` | Event dinleme doğru pattern. `ApprovalApprovedEvent` ve `ApprovalRejectedEvent` platform event'leri — dinlemek serbest. |
| `WorkOrderSalesEventListener` | Event dinleme doğru pattern. Sadece mapping eklenir (Unit 1.1), yapısı değişmez. |
| `BatchFacade` kullanımı | Zaten doğru — facade üzerinden erişim. |

---

## 5. Risk Analizi

| Risk | Olasılık | Etki | Mitigation |
|------|----------|------|------------|
| Mapping katmanı performans etkisi | Çok düşük | Düşük | Record → Record mapping, GC-friendly, allocation maliyeti ihmal edilebilir |
| RuleEngine sync davranış değişimi | Düşük | Yüksek | Adapter aynı transaction içinde çalışır, davranış değişmez. Unit test ile doğrula. |
| ApprovalEntityType String mapping hatası | Orta | Orta | Adapter'da `valueOf()` + unit test. Geçersiz String için anlamlı exception. |
| SystemUser.ID erişimi | Düşük | Düşük | Adapter platform/user'da yaşar, `SystemUser` zaten orada — sorun yok. |

---

## 6. Uygulama Sırası

```
Phase 1 (Quick Wins)
  ├─ Unit 1.1: IncomingSalesOrderLine record + listener mapping     [30 dk]
  ├─ Unit 1.2: UserTrustLevelPort + Adapter                        [45 dk]
  └─ mvn compile doğrulama

Phase 2 (Cross-BC Ports)
  ├─ Unit 2.1: ProductionOrderPort + WorkOrderCreationAdapter       [45 dk]
  ├─ Unit 2.2: ApprovalPort + ApprovalGuardAdapter                  [30 dk]
  └─ mvn compile doğrulama

Phase 3 (Guardrails)
  ├─ Unit 3.1: ArchUnit kuralları (3 yeni kural)                    [20 dk]
  ├─ Unit 3.2: Unit testler (4 test dosyası)                        [45 dk]
  └─ mvn test -Dtest="*ArchTest" doğrulama
```

**Toplam tahmini süre:** ~3.5 saat

---

## 7. Başarı Kriterleri

Phase 3 sonrası şu koşullar sağlanmalı:

- [ ] `WorkOrderService` → sıfır `sales` import, sıfır `approval` import
- [ ] `SalesOrderRuleEngine` → sıfır `production` import
- [ ] `ApprovalGuardService` → sıfır `platform.user.infra` import
- [ ] ArchUnit 3 yeni kural yeşil
- [ ] `mvn clean test-compile` sıfır hata
- [ ] Mevcut iş akışı (SalesOrder → WorkOrder → Batch) davranış değişikliği yok

---

## 8. AGENTS.md Güncellemesi (Phase 3 sonrası)

Section 19 eklenecek: **WorkOrder Cross-Module Decoupling**

İçerik: Port/Adapter pattern'inin sales ↔ production ve production ↔ approval arasında uygulanması, ACL (Anti-Corruption Layer) prensibi, event dinleme vs. direct service injection ayrımı.

# Polimorfik FK Kuralları

> Modül: Cross-Cutting (11-cross-cutting)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Polimorfik FK kullanım kuralları burada tanımlanır.

---

## Genel Bakış

Polimorfik FK (`entityType + entityId` paterni) birden fazla entity tipine tek tablodan referans verir. DB seviyesinde FK constraint uygulanamaz — veri bütünlüğü uygulama katmanında sağlanır.

---

## Polimorfik FK Kullanan Entity'ler

| Entity | Alanlar | Desteklenen entityType'lar | Döküman |
|---|---|---|---|
| ApprovalRequest | `entityType + entityId` | WORK_ORDER, PURCHASE_ORDER, RECIPE_CREATE, BATCH_CLOSE | `09-approval/approval-request.md` |
| CostCalculation | `entityType + entityId` | QUOTE, WORK_ORDER, BATCH | `06-costing/cost-calculation.md` |
| Task | `entityType + entityId` | SALES_ORDER, WORK_ORDER, BATCH, GOODS_RECEIPT, APPROVAL_REQUEST | `07-flowboard/board-task.md` |
| StockTransaction | `sourceType + sourceId` | GOODS_RECEIPT, WORK_ORDER, SALES_ORDER, STOCK_COUNT, MANUAL | `05-iwm/stock-transaction-ledger.md` |
| GoodsReceipt | `sourceType + sourceId` | BATCH, PURCHASE_ORDER, SUBCONTRACT_ORDER | `02-production/goods-receipt.md` |

---

## Kurallar

### 1. Hard Delete Yasağı

**Polimorfik FK'ların kaynak entity'leri hard delete edilemez.** Tüm entity'ler soft delete kullanır:

```
isActive = false
deletedAt = now()
```

Bu kural uygulama genelinde geçerlidir — BaseEntity'deki soft delete mekanizmasıyla sağlanır.

> **Detay:** `01-foundations/base-entity.md`

### 2. Application-Level Validation

Her polimorfik FK oluşturulurken kaynak entity'nin varlığı kontrol edilir:

```java
public interface PolymorphicSource {
    UUID getId();
    String getEntityType();
}

@Service
public class PolymorphicReferenceValidator {
    
    private final Map<String, JpaRepository<?, UUID>> repositoryMap;
    
    public boolean sourceExists(String entityType, UUID entityId) {
        JpaRepository<?, UUID> repo = repositoryMap.get(entityType);
        if (repo == null) {
            throw new IllegalArgumentException("Unknown entityType: " + entityType);
        }
        return repo.existsById(entityId);
    }
}
```

### 3. EntityType Enum Tutarlılığı

Her polimorfik FK için `entityType` enum'u ilgili entity dökümanında tanımlanır. Yeni tip eklendiğinde:
1. Enum'a yeni değer eklenir
2. `repositoryMap`'e yeni repository bağlanır
3. Bu döküman güncellenir

### 4. Sorgulama Kuralları

Polimorfik FK üzerinden JOIN yapılamaz. Bunun yerine:

```java
// YANLIŞ — SQL JOIN yapılamaz
SELECT * FROM approval_request ar 
JOIN work_order wo ON ar.entity_id = wo.id;

// DOĞRU — Uygulama seviyesinde
ApprovalRequest ar = approvalRequestRepo.findById(id);
if (ar.getEntityType() == EntityType.WORK_ORDER) {
    WorkOrder wo = workOrderRepo.findById(ar.getEntityId());
}
```

---

## Risk Matrisi

| Risk | Etki | Çözüm |
|---|---|---|
| Orphan kayıtlar | Kaynak entity silindiğinde referans kırılır | Hard delete yasağı — soft delete zorunlu |
| Yanlış entityType | Yanlış tipteki entity'e referans | Application-level validation |
| Performans | JOIN yapılamadığı için N+1 sorgu | Batch okuma + caching |
| Veri tutarlılığı | DB constraint yok | @PrePersist validation + integration test |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — 5 polimorfik FK entity, kurallar, risk matrisi |

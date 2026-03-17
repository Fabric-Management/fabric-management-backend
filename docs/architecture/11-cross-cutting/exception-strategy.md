# Exception Stratejisi

> Modül: Cross-Cutting (11-cross-cutting)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Tüm modüllerin exception hiyerarşisi burada tanımlanır.

---

## Genel Bakış

Her modül kendi `DomainException` base class'ını tanımlar. Tüm domain exception'ları ortak bir `DomainException` soyut sınıfından türer. `GlobalExceptionHandler` bu hiyerarşiyi otomatik yakalar ve uygun HTTP cevabına çevirir.

---

## Hiyerarşi

```
RuntimeException
  │
  ├── DomainException (abstract — common seviyesi)
  │     │   errorCode: String
  │     │   httpStatus: int
  │     │
  │     ├── ProductionDomainException (production base — 400) ✓ MEVCUT
  │     │     ├── InvalidStatusTransitionException (409)     ✓ MEVCUT
  │     │     ├── InsufficientStockException (422)           ✓ MEVCUT
  │     │     ├── BatchCertificationOverlapException (409)   ✓ MEVCUT
  │     │     ├── FiberDomainException                       ✓ MEVCUT
  │     │     ├── RecipeDomainException                      ✓ MEVCUT
  │     │     └── BatchDomainException                       ✓ MEVCUT
  │     │
  │     ├── OrderDomainException (order base — 400)          ⬜ YENİ
  │     │     ├── InvalidOrderStateException (409)
  │     │     ├── OrderLineValidationException (422)
  │     │     └── DuplicateOrderException (409)
  │     │
  │     ├── LogisticsDomainException (logistics base — 400)  ⬜ YENİ
  │     │     ├── InvalidShipmentStateException (409)
  │     │     ├── ShipmentTrackingException (422)
  │     │     └── DeliveryFailedException (422)
  │     │
  │     ├── FinanceDomainException (finance base — 400)      ⬜ YENİ
  │     │     ├── InvoiceStateException (409)
  │     │     ├── InvoicePaymentException (422)
  │     │     └── InvoiceOverdueException (422)
  │     │
  │     └── HumanDomainException (human base — 400)          ⬜ YENİ
  │           ├── LeaveBalanceException (422)
  │           ├── PayrollProcessingException (422)
  │           └── EmployeeComplianceException (422)
  │
  ├── ForbiddenOperationException (403)                      ✓ MEVCUT
  │     → Yetki reddi — domain kuralı değil
  │
  ├── OptimisticLockConflictException (409)                  ✓ MEVCUT
  │     → Version çakışması — altyapı
  │
  └── NotFoundException (404)                                ✓ MEVCUT (common)
        → Kayıt bulunamadı
```

---

## Ortak DomainException Base

```java
// common/infrastructure/exception/DomainException.java
public abstract class DomainException extends RuntimeException {
    
    private final String errorCode;       // "PRODUCTION_RULE_VIOLATION"
    private final int httpStatus;         // 400, 409, 422
    private final Map<String, Object> details;  // Ek bilgi (opsiyonel)
    
    protected DomainException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = new HashMap<>();
    }
    
    public DomainException withDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }
}
```

### Modül Base'leri

```java
// production/common/exception/ ✓ MEVCUT
public class ProductionDomainException extends DomainException {
    public ProductionDomainException(String message) {
        super(message, "PRODUCTION_RULE_VIOLATION", 400);
    }
}

// order/common/exception/ ⬜ YENİ
public class OrderDomainException extends DomainException {
    public OrderDomainException(String message) {
        super(message, "ORDER_RULE_VIOLATION", 400);
    }
}

// logistics/common/exception/ ⬜ YENİ
public class LogisticsDomainException extends DomainException {
    public LogisticsDomainException(String message) {
        super(message, "LOGISTICS_RULE_VIOLATION", 400);
    }
}

// finance/common/exception/ ⬜ YENİ
public class FinanceDomainException extends DomainException {
    public FinanceDomainException(String message) {
        super(message, "FINANCE_RULE_VIOLATION", 400);
    }
}

// human/common/exception/ ⬜ YENİ
public class HumanDomainException extends DomainException {
    public HumanDomainException(String message) {
        super(message, "HUMAN_RULE_VIOLATION", 400);
    }
}
```

---

## GlobalExceptionHandler

```java
// Mevcut yapıyı genişlet — tek handler tüm DomainException'ları yakalar

@ExceptionHandler(DomainException.class)
public ResponseEntity<ApiResponse<?>> handleDomainException(DomainException ex) {
    return ResponseEntity
        .status(ex.getHttpStatus())
        .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
}

// Özel handler'lar (detaylı bilgi taşıyanlar için)
@ExceptionHandler(InvalidStatusTransitionException.class)
public ResponseEntity<ApiResponse<?>> handleStatusTransition(InvalidStatusTransitionException ex) {
    Map<String, Object> details = Map.of(
        "entityType", ex.getEntityType(),
        "fromStatus", ex.getFrom(),
        "toStatus", ex.getTo()
    );
    return ResponseEntity.status(409)
        .body(ApiResponse.error("INVALID_STATUS_TRANSITION", ex.getMessage(), details));
}

@ExceptionHandler(InsufficientStockException.class)
public ResponseEntity<ApiResponse<?>> handleInsufficientStock(InsufficientStockException ex) {
    Map<String, Object> details = Map.of(
        "batchId", ex.getBatchId(),
        "requested", ex.getRequested(),
        "available", ex.getAvailable(),
        "unit", ex.getUnit()
    );
    return ResponseEntity.status(422)
        .body(ApiResponse.error("INSUFFICIENT_STOCK", ex.getMessage(), details));
}
```

---

## Mevcut Durum (Production) — Referans

| Exception | HTTP | Detay Alanları | Kullanım |
|---|---|---|---|
| `ProductionDomainException` | 400 | message | Genel production kural ihlali |
| `InvalidStatusTransitionException` | 409 | entityType, from, to | State machine geçişleri |
| `InsufficientStockException` | 422 | batchId, requested, available, unit | Reserve/consume/release |
| `BatchCertificationOverlapException` | 409 | message | Cert periyod çakışması |
| `ForbiddenOperationException` | 403 | message | Yetki reddi |
| `OptimisticLockConflictException` | 409 | entityType, entityId, clientVersion, currentVersion | Version çakışması |

---

## Modül Bazlı Exception Örnekleri

### Order (önerilen)

| Exception | HTTP | Kullanım |
|---|---|---|
| `InvalidOrderStateException` | 409 | SalesOrder state machine ihlali |
| `OrderLineValidationException` | 422 | SalesOrderLine validasyon hatası (materialId+productDesc null) |
| `DuplicateOrderException` | 409 | Aynı müşteri+ürün+tarih çakışması |

### Finance (önerilen)

| Exception | HTTP | Kullanım |
|---|---|---|
| `InvoiceStateException` | 409 | Invoice state machine ihlali |
| `InvoicePaymentException` | 422 | Ödeme tutarı > kalan tutar |
| `InvoiceOverdueException` | 422 | Vadesi geçmiş fatura üzerinde işlem |

---

## HTTP Status Code Kuralları

| Status | Ne Zaman | Örnek |
|---|---|---|
| 400 | Genel iş kuralı ihlali | "Recipe component toplamı 100 olmalı" |
| 404 | Kayıt bulunamadı | "Batch not found" |
| 409 | Durum çakışması / geçersiz geçiş | "DRAFT → SHIPPED geçişi yapılamaz" |
| 422 | Validasyon hatası (işlenemeyen veri) | "Yetersiz stok: 500 istendi, 200 var" |
| 403 | Yetki reddi | "Bu işlem için yetkiniz yok" |

---

## Implementasyon Önceliği

| Modül | Durum | Öncelik |
|---|---|---|
| Production | ✓ Tam implementasyon | — |
| Order | ⬜ Gerekli | Yüksek — SalesOrderLine eklenmesiyle birlikte |
| Finance | ⬜ Gerekli | Orta — lifecycle endpoint'leri için |
| Logistics | ⬜ Gerekli | Orta — shipment lifecycle için |
| Human | ⬜ Gerekli | Düşük — leave/payroll genişletildiğinde |

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/production-security.md` | ForbiddenOperationException kullanımı |
| `02-production/batch-production.md` | BatchStatus state machine → InvalidStatusTransitionException |
| `11-cross-cutting/status-enum-catalog.md` | Tüm status enum'ları — geçiş kuralları |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — mevcut production exception'ları + 4 modül önerisi |

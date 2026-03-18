# Optimistic Locking — Kayıp Güncelleme (Lost Update) Koruması

Bu doküman, FabricOS production modüllerinde optimistic locking (versioned concurrency control) implementasyon adımlarını içerir. Güncel iş listesi ve checklist bu dosyadadır.

## Mevcut Durum

### Var Olanlar

| Katman | Durum | Detay |
|---|---|---|
| `BaseEntity.version` (`@Version Long`) | ✅ Aktif | Tüm entity'ler miras alır. JPA her update'te otomatik artırır |
| `ObjectOptimisticLockingFailureException` handler | ✅ Aktif | `GlobalExceptionHandler` → 409 `OPTIMISTIC_LOCK` |
| PostgreSQL `version` kolonu | ✅ Tüm tablolarda | `NOT NULL DEFAULT 0` |

### Eksikler (API → Frontend Zinciri Kırık)

| Katman | Durum | Sonuç |
|---|---|---|
| DTO'larda `version` alanı | ❌ Yok | Frontend versiyon bilgisini hiç almıyor |
| Request DTO'larda `version` alanı | ❌ Yok | Frontend versiyon gönderemiyor |
| Frontend `types/production.ts` | ❌ Yok | TypeScript sözleşmesinde versiyon eksik |
| Service katmanında version karşılaştırması | ❌ Yok | Sadece JPA `@Version` → doğrudan DB'de çakışma |
| `OPTIMISTIC_LOCK` response `details` | ❌ Boş | Entity ID, expected/actual version bilgisi yok |
| Frontend conflict resolution UI | ❌ Yok | Kullanıcı ne yapacağını bilmiyor |

---

## Faz 1 — Backend DTO'lara Version Propagasyonu

**Hedef:** Tüm production response DTO'ları `version` taşısın, tüm update request'ler `version` kabul etsin.

### TODO

- [ ] **Response DTO'lara `version` ekle:**
  - `FiberDto` → `private Long version;` + `FiberDto.from(entity)` → `.version(entity.getVersion())`
  - `FiberBatchDto` → aynı pattern
  - `MaterialDto` → aynı pattern
  - İleride: `YarnBatchDto`, `FabricRollDto`, `DyeOrderDto`

- [ ] **Update Request DTO'lara `version` ekle:**
  - `CreateFiberRequest` (update için de kullanılıyor) → `private Long version;`
  - Yeni alan `@NotNull` olmamalı — create'te null, update'te zorunlu
  - Alternatif: `UpdateFiberRequest` ayrı DTO oluştur (sadece update alanları + `@NotNull version`)
  - `QuantityRequest` → `private Long version;` (reserve/release/consume)
  - İleride tüm PUT/POST-with-side-effect request'lere eklenecek

- [ ] **Service katmanında version doğrulaması:**
  ```java
  // FiberService.updateFiber()
  Fiber fiber = fiberRepository.findByTenantIdAndId(tenantId, id)
      .orElseThrow(() -> new NotFoundException("Fiber not found: " + id));
  
  if (request.getVersion() != null && !request.getVersion().equals(fiber.getVersion())) {
    throw new OptimisticLockConflictException("Fiber", id, request.getVersion(), fiber.getVersion());
  }
  ```
  - Neden JPA'nın otomatik `@Version` kontrolüne güvenmiyoruz?
  - JPA kontrolü `save()` anında çalışır — entity state zaten değişmiş olur
  - Erken kontrol: `setComposition()` çağrılmadan önce yakalayarak daha temiz

---

## Faz 2 — Custom Exception + Handler Zenginleştirme

**Hedef:** 409 yanıtında frontend'in hangi kayıt, hangi versiyon, ne zaman çakıştığını bilmesi.

### TODO

- [ ] **`OptimisticLockConflictException` oluştur:**
  - Paket: `production.common.exception` (veya `common.infrastructure.web.exception`)
  - Alanlar: `entityType`, `entityId`, `clientVersion`, `currentVersion`
  - `ProductionDomainException` extend etmesin — bu özel bir infrastructure concern

- [ ] **`GlobalExceptionHandler` güncelle:**
  ```java
  @ExceptionHandler(OptimisticLockConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ApiError handleOptimisticLockConflict(
      OptimisticLockConflictException ex, HttpServletRequest req) {
    log.warn("Optimistic lock conflict: entity={}, id={}, clientV={}, currentV={}",
        ex.getEntityType(), ex.getEntityId(), ex.getClientVersion(), ex.getCurrentVersion());
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("entityType", ex.getEntityType());
    details.put("entityId", ex.getEntityId().toString());
    details.put("clientVersion", ex.getClientVersion());
    details.put("currentVersion", ex.getCurrentVersion());
    return ApiError.of(409, "Conflict", "OPTIMISTIC_LOCK", ex.getMessage(), req.getRequestURI(), details);
  }
  ```

- [ ] **Beklenen JSON çıktı:**
  ```json
  {
    "status": 409,
    "code": "OPTIMISTIC_LOCK",
    "message": "Fiber was modified by another user. Your version: 3, current version: 5.",
    "details": {
      "entityType": "Fiber",
      "entityId": "3fa85f64-...",
      "clientVersion": 3,
      "currentVersion": 5
    }
  }
  ```

---

## Faz 3 — Frontend Sözleşme + API Client

**Hedef:** TypeScript tipleri + otomatik version tracking.

### TODO

- [ ] **`types/production.ts` güncelle:**
  - Tüm response DTO'lara `version: number;` ekle
  - Tüm update request'lere `version?: number;` ekle (create'te gönderilmez)
  
  ```typescript
  export interface FiberDto {
    // ... mevcut alanlar ...
    version: number;          // ← yeni
  }
  
  export interface CreateFiberRequest {
    // ... mevcut alanlar ...
    version?: number;         // ← update'te zorunlu, create'te undefined
  }
  
  export interface QuantityRequest {
    quantity: number;
    version?: number;         // ← reserve/release/consume'da zorunlu
  }
  ```

- [ ] **`BackendErrorCode` zaten `OPTIMISTIC_LOCK` içeriyor** — ✅ mevcut

- [ ] **`lib/api-error.ts` — version conflict helper:**
  ```typescript
  export function getConflictVersions(error: unknown): {
    clientVersion: number;
    currentVersion: number;
  } | null {
    const apiErr = parseApiError(error);
    if (apiErr?.code !== "OPTIMISTIC_LOCK") return null;
    const client = apiErr.details.clientVersion;
    const current = apiErr.details.currentVersion;
    if (typeof client !== "number" || typeof current !== "number") return null;
    return { clientVersion: client, currentVersion: current };
  }
  ```

---

## Faz 4 — Conflict Resolution UI

**Hedef:** Kullanıcı dostu çakışma bildirimi ve veri kaybını önleme.

### TODO

- [ ] **Toast notification (basit akış):**
  ```typescript
  if (isErrorCode(err, "OPTIMISTIC_LOCK")) {
    toast.warning(
      "Bu kayıt siz işlem yaparken başka bir kullanıcı tarafından güncellendi. " +
      "Veri kaybını önlemek için lütfen sayfayı yenileyin.",
      { action: { label: "Yenile", onClick: () => router.refresh() } }
    );
    return;
  }
  ```

- [ ] **Conflict Resolution Modal (gelişmiş akış — opsiyonel, Faz 5+):**
  - Modal açılır: "Bu kayıt değiştirildi"
  - Sol panel: kullanıcının yaptığı değişiklikler (local state)
  - Sağ panel: sunucudaki güncel veri (re-fetch)
  - Aksiyon butonları: "Benim değişikliklerimi uygula" (force) vs "Güncel veriyi al" (discard)
  - Force: version'ı güncel değerle set edip tekrar gönder

- [ ] **Auto-refresh pattern (Kanban board):**
  - Board her 30 saniyede veya WebSocket push ile güncellenir
  - Drag-drop sırasında 409 alınırsa kart eski yerine geri döner + toast

---

## Faz 5 — Tüm Modüllere Yaygınlaştırma

### TODO

- [ ] **Production execution modülleri:**
  - YarnBatch → version in DTO + update/consume request
  - FabricRoll → version in DTO
  - DyeOrder → version in DTO

- [ ] **Platform modülleri (ihtiyaç bazlı):**
  - `OrganizationDto` → version (çok kullanıcılı org güncelleme)
  - `UserDto` → version (profil güncelleme çakışması)
  - `TradingPartnerDto` → version

- [ ] **Toplu işlem (batch update) version stratejisi:**
  - Birden fazla kaydı aynı anda güncellerken her birinin versiyonu ayrı ayrı kontrol edilmeli
  - Kısmi başarı: 200 + `failedItems` listesi vs. tamamı-ya-da-hiçbiri (atomic)

---

## Uygulama Öncelik Sırası

```
Faz 1 (DTO version propagation)  ← ilk, backend sözleşme temeli
     │
     ▼
Faz 2 (Custom exception + rich 409) ← frontend'e anlamlı details
     │
     ▼
Faz 3 (Frontend types + api-error) ← TypeScript sözleşme
     │
     ▼
Faz 4 (Conflict resolution UI)    ← Fiber sayfaları ile birlikte
     │
     ▼
Faz 5 (Tüm modüllere yaygınlaştırma) ← Yarn/Fabric/Dye gelince
```

Faz 1-3 toplam ~2 saat iş, maksimum ROI. Faz 4 UI sayfaları ile doğal olarak gelir.

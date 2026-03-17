# CQRS Architecture — Okuma/Yazma Ayrışması

Bu doküman, FabricOS'ta CQRS (Command Query Responsibility Segregation) roadmap ve TODO backlog'unu içerir. Güncel iş listesi bu dosyadadır.

## Mevcut Durum Analizi

### Zaten Var Olanlar

| Bileşen | Durum | Konum |
|---|---|---|
| `Command` / `CommandHandler` interface | ✅ Tanımlı, yaygın kullanılmıyor | `common.infrastructure.cqrs` |
| `Query` / `QueryHandler` interface | ✅ Tanımlı, yaygın kullanılmıyor | `common.infrastructure.cqrs` |
| `UserQueryService` (read-only, cached) | ✅ Aktif | `common.platform.user.app` |
| `UserCacheInvalidationService` (event-driven evict) | ✅ Aktif | `common.platform.user.app` |
| Caffeine in-memory cache | ✅ Aktif | 10K entry, 300s TTL |
| `@Transactional(readOnly = true)` | ✅ ~192 kullanım | Servis katmanı geneli |
| Hibernate batch settings | ✅ `batch_size=20` | `application.yml` |

### Eksikler

| Bileşen | Durum |
|---|---|
| `command/` vs `query/` paket yapısı | ❌ Sadece Material modülünde var |
| Read replica veya ayrı read DataSource | ❌ Tek PostgreSQL instance |
| Materialized view / read projection | ❌ Yok |
| Redis distributed cache | ❌ Yok (sadece Caffeine in-process) |
| Dedicated read model (DTO projection) | ❌ Entity'den `XxxDto.from()` ile dönüşüm |

### Trafik Profili

- **52 repository**, ~250+ custom read metot vs ~10 custom write metot
- **143 GET** vs 95 POST vs 28 PUT vs 21 DELETE endpoint
- En read-heavy: User, Organization, Subscription, Audit, Production, Finance

---

## Faz A — Paket Yapısı Standardizasyonu (Quick Win)

**Hedef:** Tüm service class'ları `command/` ve `query/` alt paketlerine taşı. Material modülü zaten bu yapıda.

### TODO

- [ ] **Material modülünü referans al** — `material/app/command/` ve `material/app/query/` zaten var
- [ ] **Fiber modülüne uygula:**
  - `FiberService` → `FiberCommandService` (create, update, deactivate)
  - `FiberService` → `FiberQueryService` (getById, getAll, searchByName, getByMaterialId)
  - `FiberFacade` read metotları → `FiberQueryService` implements
  - `FiberQueryService` → `@Transactional(readOnly = true)` + `@Cacheable`
- [ ] **FiberBatch modülüne uygula:**
  - `FiberBatchService` → `FiberBatchCommandService` (create, reserve, release, consume)
  - `FiberBatchService` → `FiberBatchQueryService` (getAll, getById, getByFiberId)
- [ ] **Pattern'ı diğer modüllere yay:**
  - Organization, TradingPartner, Subscription, Invoice, SalesOrder
  - Kural: write metot varsa `command/`, sadece read ise `query/`

---

## Faz B — Caching Stratejisi Genişletme

**Hedef:** Sık erişilen read endpoint'leri Caffeine cache'e al, event-driven eviction ile tutarlılık sağla.

### TODO

- [ ] **Production modülü cache'leri:**
  - `FiberQueryService.getAll()` → `@Cacheable("fibers-by-tenant")`
  - `FiberQueryService.getById()` → `@Cacheable("fiber-by-id")`
  - `FiberBatchQueryService.getAll()` → `@Cacheable("fiber-batches-by-tenant")`
  - `FiberCategoryDto` listesi → `@Cacheable("fiber-categories")` (referans veri, nadiren değişir)
  - Material listesi → `@Cacheable("materials-by-tenant")`

- [ ] **Cache eviction event listener:**
  - `FiberCreatedEvent` / `FiberUpdatedEvent` → evict `fibers-by-tenant`, `fiber-by-id`
  - `FiberBatch` state change → evict `fiber-batches-by-tenant`
  - Pattern: `UserCacheInvalidationService` modelini takip et

- [ ] **Cache istatistik izleme:**
  - Caffeine `recordStats()` aktifleştir
  - `/actuator/caches` endpoint'i üzerinden hit/miss oranlarını izle
  - Hit oranı < %70 olan cache'ler → TTL veya key stratejisi gözden geçirilmeli

---

## Faz C — Read-Optimized Projections

**Hedef:** Karmaşık JOIN gerektiren read endpoint'ler için dedicated read DTO'lar + özel JPQL sorgular.

### TODO

- [ ] **FiberBatch list view projection:**
  - Mevcut: `FiberBatchDto.from(entity)` → entity load + `getAvailableQuantity()` hesabı
  - Hedef: `FiberBatchSummaryProjection` interface (Spring Data projection)
    ```java
    public interface FiberBatchSummaryProjection {
      UUID getId();
      String getBatchCode();
      String getStatus();
      BigDecimal getQuantity();
      BigDecimal getAvailableQuantity();
      String getFiberName();       // JOIN ile fiber tablosundan
      String getUnit();
    }
    ```
  - `@Query` ile tek SQL'de fiber name + batch bilgileri

- [ ] **Dashboard aggregation query'leri:**
  - Departman bazlı batch sayısı (status group by)
  - Stok özet (toplam mevcut/reserved/consumed per fiber)
  - Bu query'ler ileride materialized view olabilir

- [ ] **Kanban Board read model** (Faz 4 ile birlikte):
  - `TaskBoardProjection`: task + assignee name + department + SLA durumu tek sorguda
  - Cache: kısa TTL (30s) veya WebSocket push ile bypass

---

## Faz D — Read Replica & DataSource Ayrımı

**Hedef:** Üretimde read trafik ana DB'den ayrı sunucuya yönlensin.

### TODO

- [ ] **Çoklu DataSource konfigürasyonu:**
  ```yaml
  spring:
    datasource:
      write:
        url: jdbc:postgresql://primary:5432/fabricos
        hikari.maximum-pool-size: 20
      read:
        url: jdbc:postgresql://replica:5432/fabricos
        hikari.maximum-pool-size: 30
  ```
  - `AbstractRoutingDataSource` ile `@Transactional(readOnly = true)` → read replica'ya yönlendir
  - `@Transactional` (default) → primary'ye yönlendir

- [ ] **PostgreSQL streaming replication kurulumu:**
  - Primary → 1 async read replica
  - Replication lag izleme (< 100ms hedef)

- [ ] **Spring Boot `@ReadOnlyDataSource` custom annotation:**
  ```java
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Transactional(readOnly = true)
  public @interface ReadQuery {}
  ```

---

## Faz E — Distributed Cache (Redis) Geçişi

**Hedef:** Multi-instance deployment'ta cache tutarlılığı.

### TODO

- [ ] **Caffeine → Redis/Caffeine two-tier:**
  - L1: Caffeine in-process (sıcak veri, çok düşük latency)
  - L2: Redis (instance'lar arası paylaşım)
  - Library: `caffeine` + `spring-boot-starter-data-redis`

- [ ] **Cache invalidation stratejisi:**
  - Event-driven: domain event → `@CacheEvict` (mevcut pattern)
  - Redis pub/sub: bir instance evict edince diğerlerini bilgilendir
  - TTL fallback: Redis key'lerde TTL, Caffeine'de daha kısa TTL

- [ ] **Session & rate-limit verilerini Redis'e taşı:**
  - `TooManyRequestsException` / `RateLimitAspect` → Redis counter
  - Distributed rate limiting (sliding window)

---

## Uygulama Öncelik Sırası

```
Faz A (Paket yapısı)      ← hemen yapılabilir, sıfır risk, refactoring
     │
     ▼
Faz B (Cache genişletme)  ← en yüksek ROI, mevcut Caffeine üzerine
     │
     ▼
Faz C (Read projections)  ← Kanban board + dashboard geldiğinde
     │
     ├───────────────────────────┐
     ▼                           ▼
Faz D (Read replica)      Faz E (Redis)
← yük artınca               ← multi-instance olunca
```

Faz A ve B **bugün bile** yapılabilir — mevcut `UserQueryService` pattern'ı birebir kopyalanacak. Faz D ve E, trafik gerçekten darboğaz olmaya başladığında devreye girer.

---

## Mevcut Sayısal Benchmark Referansı

| Metrik | Değer |
|---|---|
| Toplam repository | 52 |
| Custom read metot | ~250+ |
| Custom write metot | ~10 |
| GET endpoint | 143 |
| Yazma endpoint (POST+PUT+DELETE) | 144 |
| Caffeine cache entry limit | 10,000 |
| Caffeine TTL | 300s |
| HikariCP pool (prod) | 20 bağlantı |
| `@Transactional(readOnly=true)` kullanım | ~192 |

# ProductionAccessService — Yetkilendirme

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: Production modülü yetkilendirme kuralları burada tanımlanır.

---

## Genel Bakış

Production modülündeki tüm REST endpoint'leri için **role + department** bazlı yetkilendirme. Tek bir servis (`ProductionAccessService`) üzerinden, Spring Security `@PreAuthorize` ve SpEL ile uygulanır. JWT'deki `AuthenticatedUserContext` kullanılır — ekstra DB sorgusu yok.

---

## Kullanım

```java
@PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
@PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
```

---

## Modüller ve Aksiyonlar

| Modül Sabiti | Kapsam | API Base Path |
|---|---|---|
| `FIBER` | Lif kataloğu | `/api/production/fibers` |
| `MATERIAL` | Malzeme kataloğu | `/api/production/materials` |
| `BATCH` | Parti / lot | `/api/production/batches` |
| `QUALITY_TEST` | Fiber test sonuçları | `/api/production/quality/fiber-tests` |
| `WAREHOUSE_LOCATION` | Depo lokasyonları | `/api/production/warehouse-locations` |

**Aksiyonlar:** `READ`, `WRITE`

---

## Yetki Matrisi

| Role | Department | FIBER/MATERIAL | BATCH | QUALITY_TEST | WAREHOUSE_LOCATION |
|---|---|---|---|---|---|
| ADMIN / PLATFORM_ADMIN | herhangi | R+W | R+W | R+W | R+W |
| MANAGER | R&D, Prod.Planning, Fiber&RawMat., Admin Office | R+W | R+W | — | — |
| MANAGER | Quality Control | R | R+W | R+W | R |
| MANAGER | Warehouse | R | R+W | R | R+W |
| MANAGER | Diğer prod. dept | R | R+W | R | R |
| SUPERVISOR | QC / R&D / Fiber | R | R+W | R+W | R |
| SUPERVISOR | Warehouse | R | R+W | R | R+W |
| SUPERVISOR | Diğer | R | R+W | R | R |
| WORKER / VIEWER | Herhangi prod. dept | R | R | R | R |

---

## Department Set'leri

| Set | Department Kodları |
|---|---|
| FIBER_MASTERDATA_WRITE | PRODUCTION, PRODUCTION, PROCUREMENT, MANAGEMENT |
| BATCH_WRITE | R&D, Prod.Planning, Fiber&RawMat., Yarn, Weaving, Dyeing, QC, Warehouse |
| QUALITY_TEST_WRITE | QUALITY_CONTROL, PRODUCTION, PROCUREMENT |
| WAREHOUSE_LOCATION_WRITE | WAREHOUSE, PRODUCTION |
| ALL_PRODUCTION_READ | Yukarıdakiler + PROCUREMENT |

> Department kodları `TenantSeedService.generateDepartmentCode()` ile uyumlu.

---

## Karar Akışı

```
hasPermission(auth, module, action) çağrılır
    ↓
AuthenticatedUserContext var mı?
  Hayır → fallback: READ=authenticated, WRITE=MANAGEMENT_ROLES
  Evet → evaluate(ctx, module, action)
    ↓
ADMIN / PLATFORM_ADMIN → her zaman true
READ → OPERATIONAL_ROLES her modülde; değilse department kontrolü
WRITE → MANAGER + modül WRITE_DEPARTMENTS; SUPERVISOR sadece BATCH/QC/WH
```

---

## Diğer Modüllerin Yetkilendirme Durumu

| Modül | Yetkilendirme | Not |
|---|---|---|
| Production | ProductionAccessService ✓ | Role + department, detaylı |
| Order | @PreAuthorize yok | Sadece auth/tenant filter |
| Logistics | @PreAuthorize yok | Sadece auth/tenant filter |
| Finance | @PreAuthorize yok | Sadece auth/tenant filter |
| Human | isAuthenticated() | Self-service + internal endpoint |

> **Açık karar:** Tüm modüllerde tutarlı yetkilendirme stratejisi uygulanmalı.

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/user-auth.md` | Role enum, AuthenticatedUserContext |
| `01-foundations/organization-department.md` | Department kodları, TenantSeedService |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — gerçek koddan, diğer modül karşılaştırma eklendi |

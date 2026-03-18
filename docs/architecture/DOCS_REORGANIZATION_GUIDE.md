# Docs Klasör Reorganizasyonu — Uygulama Rehberi

> Tarih: 2026-03-17  
> Amaç: Mevcut docs/ yapısını yeni mimari dökümanlarla birleştirmek

---

## Yeni Yapı

```
docs/
├── README.md                              ← ANA INDEX (güncellendi)
│
├── architecture/                          ← MİMARİ DÖKÜMANLAR (yeni, 14 kategori)
│   ├── README.md                          ← Sistem haritası (yeni ana döküman)
│   ├── implementation-roadmap.md          ← İmplementasyon yol haritası
│   │
│   ├── 01-foundations/                    ← Temel yapılar
│   │   ├── base-entity.md
│   │   ├── reference-tables.md
│   │   ├── user-auth.md
│   │   ├── organization-department.md
│   │   └── trading-partner.md
│   │
│   ├── 02-production/                     ← Üretim zinciri
│   │   ├── material-fiber.md
│   │   ├── recipe.md
│   │   ├── work-order.md
│   │   ├── batch-production.md
│   │   ├── batch-lineage.md
│   │   ├── inventory.md
│   │   ├── quality-fiber.md
│   │   ├── goods-receipt.md
│   │   ├── warehouse-location.md
│   │   ├── fiber-request.md
│   │   └── production-security.md
│   │
│   ├── 03-sales/
│   │   ├── sales-order.md
│   │   ├── quote-approval.md
│   │   ├── sample-management.md
│   │   ├── discount-policy.md
│   │   └── product-catalog.md
│   │
│   ├── 04-procurement/
│   │   ├── supplier-rfq.md
│   │   ├── supplier-quote.md
│   │   ├── purchase-order.md
│   │   └── subcontract-order.md
│   │
│   ├── 05-iwm/
│   │   ├── location.md
│   │   ├── stock-transaction-ledger.md
│   │   ├── stock-reservation.md
│   │   ├── stock-rules.md
│   │   ├── stock-count.md
│   │   ├── transfer.md
│   │   ├── rma.md
│   │   └── manual-adjustment.md
│   │
│   ├── 06-costing/
│   │   ├── cost-structure.md
│   │   ├── price-list.md
│   │   ├── cost-calculation.md
│   │   └── exchange-rate-history.md
│   │
│   ├── 07-flowboard/
│   │   ├── board-task.md
│   │   ├── smart-task-generator.md
│   │   ├── task-details.md
│   │   ├── recurring-tasks.md
│   │   ├── escalation.md
│   │   └── performance.md
│   │
│   ├── 08-notification-i18n/
│   │   ├── i18n.md
│   │   └── notification-hub.md
│   │
│   ├── 09-approval/
│   │   ├── trust-level-policy.md
│   │   └── approval-request.md
│   │
│   ├── 10-mobile-offline/
│   │   └── offline-sync.md
│   │
│   ├── 11-cross-cutting/
│   │   ├── event-catalog.md
│   │   ├── status-enum-catalog.md
│   │   ├── polymorphic-fk-rules.md
│   │   ├── jsonb-strategy.md
│   │   ├── exception-strategy.md
│   │   ├── auth-strategy.md
│   │   └── cari-hesap-iskelet.md
│   │
│   ├── 12-human/
│   │   └── human-overview.md
│   │
│   ├── 13-finance/
│   │   └── finance-invoice.md
│   │
│   └── 14-logistics/
│       └── logistics-shipment.md
│
├── analysis/                              ← ANALİZ RAPORLARI (mevcut, korunuyor)
│   ├── COOKIE_AUTH_SENIOR_CODE_REVIEW.md
│   └── FIBER_ENTITY_DATA_CONSISTENCY_ANALYSIS.md
│
├── code-review/                           ← KOD İNCELEME (mevcut, korunuyor)
│   ├── ADDRESS_FORM_CODE_REVIEW.md
│   ├── BATCH_FRONTEND_BACKEND_CONTRACT_AUDIT.md
│   ├── BATCH_FRONTEND_CONSUMER_AUDIT.md
│   ├── BATCH_INHERITANCE_CHAT_CODE_REVIEW.md
│   ├── NOTIFICATION_MODULE_CODE_REVIEW.md
│   ├── ONBOARDING_5STEP_REFACTOR_CODE_REVIEW.md
│   ├── ONBOARDING_ADDRESS_CHAT_CODE_REVIEW.md
│   └── ONBOARDING_CHAT_REFACTOR_CODE_REVIEW.md
│
├── archive/                               ← ARŞİV (mevcut, korunuyor)
│   ├── COMPANY_TO_ORGANIZATION_REFACTOR.md
│   ├── FIBER_BACKEND_OZET_RAPOR.md
│   ├── FIBER_ENTITY_REPORT.md
│   ├── LINEAGE_ARCHITECTURE.md
│   ├── README.md
│   └── UNIVERSAL_BATCH_ROADMAP.md
│
├── todo/                                  ← YAPILACAKLAR (mevcut — gözden geçirilmeli)
│   ├── INDEX.md
│   ├── BATCH_GENERALIZATION_TODO.md       → ✅ Tamamlandı: batch-production.md V2.0
│   ├── CQRS_TODO.md                      → ✅ Tamamlandı: inventory.md
│   ├── CROSS_MODULE_LINEAGE_TODO.md      → ✅ Tamamlandı: batch-lineage.md
│   ├── LINEAGE_TODO.md                   → ✅ Tamamlandı: batch-lineage.md
│   ├── LINEAGE_ARCHITECTURE_TODO_LEGACY.md → archive'a taşınabilir
│   ├── OPTIMISTIC_LOCKING_TODO.md        → base-entity.md'de belgelendi
│   ├── PRODUCTION_SYSTEM_ROADMAP_TODO.md → implementation-roadmap.md ile değiştirildi
│   └── WIP_LOCATION_TODO.md             → ✅ Tamamlandı: location.md (MACHINE tipi)
│
└── ddl/                                   ← VERİTABANI ŞEMALARI (architecture/ddl'den taşındı)
    └── production_execution_batch.sql
```

---

## Taşıma Komutları

Aşağıdaki komutları sırasıyla çalıştır:

```bash
# 1. Yeni architecture dizinini oluştur
mkdir -p docs/architecture

# 2. Yeni dökümanları architecture/ altına kopyala
# (Claude'dan indirdiğin docs/ klasörünün İÇERİĞİNİ buraya kopyala)
cp -r <indirilen-docs>/* docs/architecture/

# 3. Mevcut DDL'i taşı
mv docs/architecture/ddl docs/ddl

# 4. features/ boşsa kaldır (veya tut — gelecekte kullanılabilir)
rmdir docs/features 2>/dev/null

# 5. Tamamlanan TODO'ları archive'a taşı
mv docs/todo/LINEAGE_ARCHITECTURE_TODO_LEGACY.md docs/archive/
```

---

## Kök README.md Güncellemesi

Mevcut `docs/README.md` güncellenmeli:

```markdown
# Fabric Management Backend — Docs

## Klasör Yapısı

| Klasör | Açıklama | İçerik |
|---|---|---|
| `architecture/` | **Mimari dökümanlar** — sistemin tek gerçeği | 60 döküman, 14 kategori, implementasyon yol haritası |
| `analysis/` | Analiz raporları | Tekil inceleme ve audit raporları |
| `code-review/` | Kod inceleme notları | Modül/özellik bazlı review raporları |
| `archive/` | Arşiv | Eski dökümanlar, tamamlanmış geçiş planları |
| `todo/` | Yapılacaklar | Aktif todo'lar — tamamlananlar archive'a taşınır |
| `ddl/` | Veritabanı şemaları | SQL DDL dosyaları |

## Başlangıç Noktası

**Mimari dökümanlar:** `architecture/README.md` — sistem haritası  
**İmplementasyon planı:** `architecture/implementation-roadmap.md` — faz bazlı yol haritası
```

---

## TODO Klasörü — Gözden Geçirme

Bu TODO dosyaları artık mimari dökümanlarla karşılandı:

| TODO Dosyası | Durum | Karşılayan Döküman |
|---|---|---|
| `BATCH_GENERALIZATION_TODO.md` | ✅ Tamamlandı | `architecture/02-production/batch-production.md` V2.0 — evrensel Batch modeli |
| `CQRS_TODO.md` | ✅ Tamamlandı | `architecture/02-production/inventory.md` — CQRS InventoryTransaction + Balance |
| `CROSS_MODULE_LINEAGE_TODO.md` | ✅ Tamamlandı | `architecture/02-production/batch-lineage.md` — parent→child + attribute inheritance |
| `LINEAGE_TODO.md` | ✅ Tamamlandı | `architecture/02-production/batch-lineage.md` |
| `LINEAGE_ARCHITECTURE_TODO_LEGACY.md` | 📦 Archive'a taşı | Eski, batch-lineage.md ile değiştirildi |
| `OPTIMISTIC_LOCKING_TODO.md` | ✅ Belgelendi | `architecture/01-foundations/base-entity.md` — `@Version` |
| `PRODUCTION_SYSTEM_ROADMAP_TODO.md` | 🔄 Değiştirildi | `architecture/implementation-roadmap.md` — 13 faz, detaylı plan |
| `WIP_LOCATION_TODO.md` | ✅ Tamamlandı | `architecture/05-iwm/location.md` — MACHINE LocationType |

**Önerilen aksiyon:** Tamamlanmış TODO'ları `archive/` klasörüne taşı. `todo/INDEX.md`'yi güncelle — sadece aktif todo'lar kalsın.

---

## Archive Klasörü — Mevcut Dosya Değerlendirmesi

| Arşiv Dosyası | Durumu |
|---|---|
| `COMPANY_TO_ORGANIZATION_REFACTOR.md` | Tamamlanmış geçiş — arşivde kalsın |
| `FIBER_BACKEND_OZET_RAPOR.md` | Eski rapor — arşivde kalsın |
| `FIBER_ENTITY_REPORT.md` | `material-fiber.md` ile değiştirildi — arşivde kalsın |
| `LINEAGE_ARCHITECTURE.md` | `batch-lineage.md` ile değiştirildi — arşivde kalsın |
| `UNIVERSAL_BATCH_ROADMAP.md` | `batch-production.md` V2.0 ile değiştirildi — arşivde kalsın |

---

## Özet — Ne Değişti?

| Önce | Sonra |
|---|---|
| 27 dosya, 8 dizin | 87+ dosya, 20+ dizin |
| Mimari döküman yok | 60 mimari döküman (`architecture/`) |
| TODO'lar karışık | TODO'lar gözden geçirildi, tamamlananlar arşive |
| DDL `architecture/ddl/` altında | DDL kök `ddl/` altında |
| `features/` boş | Kaldırılabilir veya gelecek için tutulabilir |

**Temel kural:** `architecture/` klasörü sistemin **tek gerçeği** (single source of truth). Herhangi bir entity, akış veya karar sorgulandığında ilk bakılacak yer burası.

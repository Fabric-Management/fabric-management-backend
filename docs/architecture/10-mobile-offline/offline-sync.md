# Offline Mimari & Sync Stratejisi

> Modül: Mobil & Offline (10-mobile-offline) | Versiyon: 1.0 | Son güncelleme: 2026-03-17
> Kanonik kaynak: Offline sync stratejisi, 4 çakışma tipi, sync sırası burada tanımlanır.

## Offline Alanları (Entity'lere eklenen)

offlineId (cihaz UUID), deviceId, offlineCreatedAt, syncedAt, syncStatus (PENDING/SYNCED/CONFLICT/RESOLVED).

## 4 Çakışma Tipi

**TİP 1 — Aynı müşteri çakışması:** İki pazarlamacı aynı müşteriye offline Quote. Tespit: customerId + moduleType + aynı gün. Çözüm: Manager seçer, diğeri CANCELLED, gerekçe zorunlu.

**TİP 2 — Fiyat değişikliği çakışması:** PriceList offline'da güncellenmiş. Tespit: offlineCreatedAt < PriceList.validFrom. Çözüm: Eski fiyatla devam (manager onayı) veya yeni fiyatla güncelle.

**TİP 3 — Stok çakışması:** Seçilen lot başkasına satılmış. Tespit: StockReservation oluşturulamıyor. Çözüm: CONFLICT + yeni lot önerisi.

**TİP 4 — Müşteri bilgisi çakışması:** Aynı yeni müşteri iki kez offline eklendi. Tespit: taxId/email eşleşmesi. Çözüm: Mevcut kayda bağlanır.

## Sync Sırası

1. Referans veriler (TradingPartner, ProductCatalog) — merge
2. Quote — manager kararı
3. SampleRequest — merge
4. SalesOrder — manager kararı

## Offline'da Mümkün Olmayan İşlemler

StockReservation (stok güncel değil), WorkOrder oluşturma (onay sistemi), fiyat kontrolü (maliyet güncel değil — "son bilinen fiyat" uyarısı).

## Offline Akış

Fuar sabahı internette → katalog + müşteri listesi indirilir → gün boyu offline → SQLite'a yazılır → internet gelince SyncQueue işlenir → çakışma kontrolü.

# Location — Birleştirilmiş Lokasyon Hiyerarşisi

> Modül: IWM (05-iwm)  
> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: WarehouseLocation entity'sinin TEK tanım yeri burasıdır.

---

## Genel Bakış

Tüm depo ve üretim lokasyonlarının hiyerarşik tanımı. Self-referential ağaç yapısı. Hem gerçek koddaki `WarehouseLocation` (kapasite, barkod, linked machine) hem IWM tasarımındaki (SITE seviyesi, warehouseType) özellikler birleştirilmiştir.

### Birleştirme Kararı

Önceden iki ayrı lokasyon modeli vardı: `production.production_execution_warehouse_location` (kodda) ve `iwm.location` (dökümanda). Bu iki model tek tabloya birleştirildi — çifte lokasyon karmaşasını önlemek için.

---

## WarehouseLocation

> Tablo: `iwm.warehouse_location` (birleştirilmiş)  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama | Kaynak |
|---|---|---|---|---|
| `parentId` | UUID | Hayır | FK → WarehouseLocation (self) — null ise kök (SITE) | Her ikisi |
| `code` | String (50) | Evet | Benzersiz kod — `IST-WH1-A-001` | Her ikisi |
| `name` | String (255) | Evet | Lokasyon adı | Her ikisi |
| `description` | String (TEXT) | Hayır | Açıklama | Kod |
| `type` | LocationType (Enum) | Evet | Bkz. Lokasyon Tipleri | Birleştirilmiş |
| `warehouseType` | WarehouseType (Enum) | Hayır | RAW/FINISHED/WIP/REJECT/SAMPLE — sadece WAREHOUSE'da | IWM |
| `status` | Enum | Evet | ACTIVE / INACTIVE / MAINTENANCE | Kod |
| `storageCondition` | String | Hayır | Depolama koşulu (sıcaklık, nem) | Kod |
| `path` | String | Hayır | Tam hiyerarşi yolu — `IST/WH1/A/A-01-01` | Kod |
| `level` | Integer | Hayır | Hiyerarşi seviyesi (0=SITE, 1=WAREHOUSE...) | Kod |
| `sortOrder` | Integer | Hayır | Sıralama | Kod |
| `barcode` | String | Hayır | Lokasyon barkodu — mobil tarama | Kod |
| `address` | JSONB | Hayır | Fiziksel adres — sadece SITE seviyesinde | IWM |
| `addressId` | UUID | Hayır | FK → Address | Kod |
| `maxWeightKg` | Decimal | Hayır | Maksimum ağırlık kapasitesi (kg) | Kod |
| `currentWeightKg` | Decimal | Hayır | Mevcut ağırlık | Kod |
| `maxVolumeM3` | Decimal | Hayır | Maksimum hacim kapasitesi (m³) | Kod |
| `currentVolumeM3` | Decimal | Hayır | Mevcut hacim | Kod |
| `linkedMachineId` | UUID | Hayır | Bağlı makine — MACHINE tipi için | Kod |
| `isActive` | Boolean | Evet | BaseEntity soft delete | Her ikisi |

---

## LocationType — Birleştirilmiş

| Tip | Açıklama | Kaynak |
|---|---|---|
| `SITE` | Fiziksel adres — fabrika, depo binası | IWM tasarımı |
| `WAREHOUSE` | Depo — site içinde | Her ikisi |
| `ZONE` | Bölge — depo içinde | Her ikisi |
| `AISLE` | Koridor — zone içinde geçiş yolu | Kod |
| `BIN` | Raf / göz — en küçük depolama birimi | Her ikisi |
| `MACHINE` | Makine — WIP takibi | Her ikisi |
| `PRODUCTION_LINE` | Üretim hattı — proses takibi | Kod |

## WarehouseType

| Değer | İçerik | Kullanım |
|---|---|---|
| `RAW` | Hammadde | PO teslim alımı → buraya girer |
| `FINISHED` | Mamul | Batch COMPLETED → buraya girer |
| `WIP` | Yarı mamul | Üretim arasında |
| `REJECT` | Fire / ret | QC_FAILED → buraya taşınır |
| `SAMPLE` | Numune | SampleRequest → buradan çıkar |

---

## Hiyerarşi Örnekleri

### Tam Hiyerarşi (Büyük Fabrika — Çoklu Site)

```
İstanbul Fabrikası (SITE) — address: {...}
  ├── Ana Depo (WAREHOUSE — RAW)
  │     ├── A Bölgesi (ZONE)
  │     │     ├── Koridor 1 (AISLE)
  │     │     │     ├── A-01-01 (BIN) — barcode: LOC-A0101
  │     │     │     └── A-01-02 (BIN) — barcode: LOC-A0102
  │     │     └── Koridor 2 (AISLE)
  │     └── B Bölgesi (ZONE)
  ├── Mamul Deposu (WAREHOUSE — FINISHED)
  │     └── C Bölgesi (ZONE)
  ├── Dokuma Makinesi 1 (MACHINE) — linkedMachineId
  ├── Üretim Hattı A (PRODUCTION_LINE)
  ├── Ret Deposu (WAREHOUSE — REJECT)
  └── Numune Deposu (WAREHOUSE — SAMPLE)

İzmir Depo (SITE) — address: {...}
  └── Depo (WAREHOUSE — FINISHED)
        └── Raf-01 (BIN)

Bursa Fason Merkez (SITE) — address: {...}
  └── Geçici Depo (WAREHOUSE — WIP)
```

### Minimal (Küçük İşletme)

```
Merkez (SITE)
  └── Depo (WAREHOUSE — RAW)
        └── Raf-01 (BIN)
```

---

## Varsayılan Lokasyon Atama

GoodsReceipt CONFIRMED olduğunda hedef lokasyon:

| Batch.sourceType | Varsayılan Hedef |
|---|---|
| `INTERNAL_PRODUCTION` | FINISHED warehouse (aynı site) |
| `PURCHASE` | RAW warehouse (aynı site) |
| `SUBCONTRACT` | FINISHED warehouse (aynı site) |
| `RETURN` | QC sonrası karar (FINISHED veya REJECT) |
| `INITIAL_STOCK` | Kullanıcı seçer |

Depo personeli varsayılanı override edebilir — Zone/Bin seviyesinde seçim yapabilir.

---

## MACHINE Lokasyon — WIP Takibi

Üretim makineleri birer lokasyon. "Şu an makinelerde ne kadar malzeme var?" sorusu StockLedger/InventoryBalance üzerinden cevaplanır.

```
WorkOrder başladı → Batch CONSUME
  InventoryTransaction: OUT (RAW WAREHOUSE → MACHINE)
    ↓
Üretim devam — malzeme MACHINE'de görünür
    ↓
Batch COMPLETED → Yeni Batch doğdu
  InventoryTransaction: IN (MACHINE → FINISHED WAREHOUSE)
```

---

## Kapasite Yönetimi

```
Ana Depo (WAREHOUSE):
  maxWeightKg: 50,000     currentWeightKg: 32,000
  maxVolumeM3: 1,000      currentVolumeM3: 650
  → Doluluk: %64 (ağırlık), %65 (hacim)

A-01-01 (BIN):
  maxWeightKg: 500        currentWeightKg: 320
  → Doluluk: %64
```

> İleride: kapasite doluluk uyarısı → NotificationHub bildirimi.

---

## API

Base path: `/api/production/warehouse-locations` (mevcut kod)

> **Geçiş planı:** Endpoint path ileride `/api/iwm/locations`'a taşınabilir.

| Endpoint | Metod | Açıklama |
|---|---|---|
| `/` | POST | Lokasyon oluştur |
| `/` | GET | Tümünü listele |
| `/{id}` | GET | Detay |
| `/{id}` | PUT | Güncelle |
| `/{id}/status` | PATCH | Status değiştir |
| `/{id}/deactivate` | POST | Pasif yap |
| `/tree` | GET | Ağaç yapısında listele |

**Yetkilendirme:** `ProductionAccessService` — WAREHOUSE_LOCATION READ/WRITE.

---

## İlişki Özeti

```
WarehouseLocation (self-referential, SITE → WAREHOUSE → ZONE → BIN)
  │
  ├──→ Batch.locationId (parti nerede?)
  ├──→ InventoryBalance.locationId (lokasyon bakiyesi)
  ├──→ InventoryTransaction.locationId (stok hareketleri)
  ├──→ StockTransaction.locationId (IWM stok hareketleri — ileride)
  ├──→ StockLedger.locationId (IWM anlık bakiye — ileride)
  ├──→ StockReservation.locationId (lot ayırma)
  ├──→ MinStockRule.locationId (minimum stok kuralı)
  ├──→ StockCount.locationId (stok sayımı)
  └──→ StockTransfer.fromLocationId / toLocationId (transfer)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `02-production/batch-production.md` | Batch.locationId |
| `02-production/inventory.md` | InventoryBalance.locationId |
| `05-iwm/stock-transaction-ledger.md` | StockTransaction.locationId |
| `05-iwm/stock-reservation.md` | StockReservation.locationId |
| `05-iwm/transfer.md` | StockTransfer.fromLocationId, toLocationId |
| `02-production/goods-receipt.md` | GoodsReceipt → varsayılan lokasyon atama |

---

## Açık Kararlar

- [ ] Şema geçişi: `production` → `iwm` zamanlaması
- [ ] API path geçişi: `/api/production/warehouse-locations` → `/api/iwm/locations`
- [ ] Kapasite aşım uyarısı — NotificationHub entegrasyonu
- [ ] IN_TRANSIT sanal lokasyon — depolar arası transferde

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | Birleştirilmiş yapı — kod (WarehouseLocation) + IWM tasarım (SITE, warehouseType) |
| 1.0 | 2026-03-17 | İlk versiyon (IWM tasarımı) |

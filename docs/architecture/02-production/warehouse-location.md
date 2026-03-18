# WarehouseLocation — Production Referansı

> Modül: Üretim Zinciri (02-production)  
> Versiyon: 2.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17

---

## Birleştirme Notu

WarehouseLocation artık `05-iwm/location.md` dökümanında **tek kanonik kaynak** olarak tanımlanır. Production modülü bu entity'yi kullanır ama tanımlamaz.

**Kanonik kaynak:** `05-iwm/location.md`

### Production Modülünden Kullanım

| Entity | Alan | Açıklama |
|---|---|---|
| Batch | `locationId` | Parti nerede? |
| InventoryBalance | `locationId` | Lokasyon bazlı bakiye |
| InventoryTransaction | `locationId` | Stok hareketi lokasyonu |

### API (Mevcut)

Mevcut kodda API path: `/api/production/warehouse-locations`  
İleride `iwm` şemasına taşınabilir: `/api/iwm/locations`

### Yetkilendirme

`ProductionAccessService` — WAREHOUSE_LOCATION READ/WRITE.

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 2.0 | 2026-03-17 | 05-iwm/location.md'ye birleştirildi — bu dosya artık referans |
| 1.0 | 2026-03-17 | İlk versiyon (detaylı) |

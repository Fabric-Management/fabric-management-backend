# Transfer — Depolar Arası Stok Hareketi

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: StockTransfer entity'si burada tanımlanır.

---

## Genel Bakış

4 transfer senaryosu: aynı site içi (anlık), farklı adresler arası (IN_TRANSIT ile iki aşamalı), WIP makineden depoya, ve numune deposuna.

---

## StockTransfer

> Tablo: `iwm.stock_transfer`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `transferNumber` | String | Evet | **Otomatik** — `TRF-2024-001` |
| `transferType` | Enum | Evet | INTERNAL / INTER_SITE / WIP / SAMPLE |
| `fromLocationId` | UUID | Evet | FK → Location |
| `toLocationId` | UUID | Evet | FK → Location |
| `materialId` | UUID | Evet | FK → Material |
| `lotNumber` | String | Evet | Lot numarası |
| `qty` | Decimal | Evet | Transfer miktarı |
| `unit` | Enum | Evet | KG / MT / PIECE |
| `status` | Enum | Evet | DRAFT / IN_TRANSIT / COMPLETED |
| `sourceType` | Enum | Hayır | BATCH / SAMPLE_REQUEST — bağlantılı kayıt |
| `sourceId` | UUID | Hayır | İlgili kaydın id'si |
| `dispatchedAt` | Timestamp | Hayır | Çıkış zamanı |
| `receivedAt` | Timestamp | Hayır | Varış zamanı |
| `vehicleInfo` | String | Hayır | Taşıma bilgisi — INTER_SITE için |
| `notes` | String (TEXT) | Hayır | Notlar |

---

## 4 Transfer Senaryosu

| Tip | Açıklama | Stok Güncelleme |
|---|---|---|
| `INTERNAL` | Aynı site içi — bin'den bin'e | Anlık, tek adım |
| `INTER_SITE` | Farklı adresler arası | İki aşamalı — IN_TRANSIT ile |
| `WIP` | Makine → mamul depo | Batch ile bağlantılı |
| `SAMPLE` | Herhangi depo → numune deposu | SampleRequest ile bağlantılı |

---

## IN_TRANSIT Akışı (Farklı Adresler)

```
Transfer başlatıldı
    ↓
StockTransaction: TRANSFER (out)
  from: İstanbul FINISHED
  to:   IN_TRANSIT lokasyonu
  StockLedger: İstanbul stok düşer
    ↓
Mal yolda — IN_TRANSIT lokasyonunda görünür, kullanılamaz
    ↓
Depo personeli "Teslim alındı" işaretler
    ↓
StockTransaction: TRANSFER (in)
  from: IN_TRANSIT
  to:   İzmir FINISHED
  StockLedger: İzmir stok artar
```

---

## Onay Mekanizması

Onay gerekmez — depo personeli direkt yapar. FlowBoard'da bilgi amaçlı task açılır. Manager isterse müdahale edebilir.

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `05-iwm/location.md` | fromLocationId, toLocationId |
| `05-iwm/stock-transaction-ledger.md` | TRANSFER transaction |
| `02-production/batch-production.md` | WIP transfer (MACHINE → FINISHED) |
| `03-sales/sample-management.md` | SAMPLE transfer |
| `11-cross-cutting/event-catalog.md` | TransferCompleted |

---

## Açık Kararlar

- [ ] Transfer onay akışı — büyük miktarlar için manager onayı gerekli mi?

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

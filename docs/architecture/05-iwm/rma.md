# RMA — İade Yönetimi

> Modül: IWM (05-iwm)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: RMA (ReturnMerchandiseAuthorization) entity'si burada tanımlanır.

---

## Genel Bakış

İki iade senaryosu: önceden anlaşılmış (pazarlamacı RMA açar) ve habersiz gelmiş (depo personeli kaydeder, onay beklenir). İade nedeni lokasyon kararını belirler. 6 boyutlu analitik raporlamayı destekler.

---

## RMA

> Tablo: `iwm.rma`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `rmaNumber` | String | Evet | **Otomatik** — `RMA-2024-001` |
| `salesOrderId` | UUID | Evet | FK → SalesOrder |
| `customerId` | UUID | Evet | FK → TradingPartner |
| `status` | RMAStatus (Enum) | Evet | PENDING / APPROVED / RECEIVED / PROCESSED / REJECTED |
| `approvedBy` | UUID | Hayır | FK → User |
| `returnMethod` | Enum | Hayır | CREDIT / OFFSET |
| `supplierId` | UUID | Hayır | Ürünün geldiği tedarikçi — analitik |
| `lotNumber` | String | Hayır | Lot numarası — analitik |
| `recipeId` | UUID | Hayır | Recipe bağlantısı — analitik |
| `defectCategory` | Enum | Evet | QUALITY / WRONG_ITEM / EXCESS / CUSTOMER_CHANGE / DAMAGE |
| `defectDetail` | String (TEXT) | Hayır | Spesifik sorun açıklaması |
| `cargoCompany` | String | Hayır | Hasar iadelerinde kargo firması |
| `photoEvidence` | JSONB | Hayır | Fotoğraf kanıtı URL listesi |

---

## İki Senaryo

**Senaryo 1 — Önceden anlaşılmış:**
```
Pazarlamacı müşteriyle anlaştı → RMA açtı (PENDING)
Manager onaylar → APPROVED
Müşteriye iade talimatı gönderildi
Mal depoya geldi → RECEIVED → QC sonrası PROCESSED
```

**Senaryo 2 — Habersiz geldi:**
```
Mal depoya geldi (habersiz)
Depo personeli RMA kaydı oluşturur (PENDING)
Pazarlamacı/Manager bildirim alır
Onaylarsa → APPROVED → RECEIVED → PROCESSED
Reddederse → REJECTED → müşteriye geri gönderilir
```

---

## İade Nedeni → Lokasyon Kararı

| defectCategory | Lokasyon | Açıklama |
|---|---|---|
| `QUALITY` | REJECT deposu | Yeniden satılamaz |
| `DAMAGE` | REJECT deposu | Hasar görmüş |
| `WRONG_ITEM` | QC sonrası karar | Kontrol edilir |
| `EXCESS` | Direkt FINISHED | Satılabilir |
| `CUSTOMER_CHANGE` | QC sonrası karar | Kontrol edilir |

---

## İade Analitik — 6 Boyut

| Rapor | Soru |
|---|---|
| Tedarikçi bazlı | "Supplier A'dan gelen ürünlerin iade oranı?" |
| Ürün / recipe bazlı | "Hangi recipe sürekli sorunlu?" |
| Lot bazlı | "Hangi lot'ta sorun yoğunlaşıyor?" |
| Müşteri bazlı | "Kim ne kadar, hangi nedenle iade yapıyor?" |
| Kargo bazlı | "Hangi kargo firmasında hasar oranı yüksek?" |
| Dönemsel trend | "Mevsime göre iade oranı değişiyor mu?" |

---

## Cari Hesap Bağlantısı

| returnMethod | Cari İşlem |
|---|---|
| `CREDIT` | CREDIT_NOTE — müşteriye para iadesi |
| `OFFSET` | OFFSET — sonraki siparişten düşülür |

> **Detay:** `11-cross-cutting/cari-hesap-iskelet.md`

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `03-sales/sales-order.md` | RMA.salesOrderId |
| `01-foundations/trading-partner.md` | RMA.customerId, supplierId |
| `05-iwm/stock-transaction-ledger.md` | RMA RECEIVED → RETURN transaction |
| `05-iwm/stock-rules.md` | ReturnRateRule — iade oranı eşiği |
| `11-cross-cutting/event-catalog.md` | RmaReceived, RmaPendingApproval |
| `11-cross-cutting/cari-hesap-iskelet.md` | CREDIT_NOTE / OFFSET |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon |

# TradingPartner & Sertifikasyon

> Modül: Temel Yapılar (01-foundations)  
> Versiyon: 1.0 | Durum: Aktif  
> Son güncelleme: 2026-03-17  
> Kanonik kaynak: TradingPartner, TradingPartnerRegistry, PartnerCertification burada tanımlanır.

---

## Genel Bakış

TradingPartner tedarikçi, müşteri veya fason firma gibi iş ortaklarını temsil eder. Platform seviyesinde `TradingPartnerRegistry` golden record olarak yaşar; her tenant kendi `TradingPartner` kaydını `registry_id` ile bağlar. Sertifika bilgileri ayrı `PartnerCertification` tablosunda yönetilir.

---

## 1. TradingPartnerRegistry

> Platform seviyesi golden record. Aynı firma farklı tenant'lar tarafından kullanıldığında tek kayıt.

Detaylar implementasyon aşamasında tanımlanacak.

---

## 2. TradingPartner

> Tablo: `common_company.common_trading_partner`  
> `BaseEntity`'den miras alır.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `registry` | TradingPartnerRegistry (ManyToOne) | Evet | Platform golden record; `registry_id` FK |
| `customName` | String (255) | Hayır | Tenant'ın bu partner için özel adı/alias |
| `partnerType` | PartnerType (Enum) | Evet | SUPPLIER / CUSTOMER / BOTH / FASON / SERVICE_PROVIDER |
| `status` | PartnerStatus (Enum) | Evet | ACTIVE / SUSPENDED / BLOCKED / PENDING — varsayılan ACTIVE |
| `relationshipMeta` | JSONB | Hayır | Ödeme koşulları, kredi limiti, notlar vb. |
| `organizationId` | UUID | Hayır | Partner kullanıcıları için bağlı Organization |
| `legacyCompanyId` | UUID | Hayır | Eski Company kaydına referans — migrasyon için |

**Unique kısıt:** `(tenant_id, registry_id)`

### PartnerType Enum

| Değer | Açıklama |
|---|---|
| `SUPPLIER` | Hammadde tedarikçisi |
| `CUSTOMER` | Müşteri |
| `BOTH` | Hem tedarikçi hem müşteri |
| `FASON` | Fason üretici |
| `SERVICE_PROVIDER` | Hizmet sağlayıcı (kargo, lab vb.) |

### Helper Metodlar

| Metod | Açıklama |
|---|---|
| `getDisplayName()` | `customName` varsa onu, yoksa Registry'den official name |
| `isOnPlatform()` | Partner platformda kayıtlı mı |
| `getLinkedTenantId()` | Partner'ın kendi tenant'ı (varsa) |
| `getTaxId()` | Registry'den vergi numarası |
| `getOfficialName()` | Registry'den resmi ad |
| `getCountry()` | Registry'den ülke |

---

## 3. PartnerCertification

> Tablo: `common_company.partner_certification`  
> `BaseEntity`'den miras alır.

**Yapısal karar:** Eski `TradingPartnerCertification` ve `OrganizationCertification` tabloları bu tek tabloda birleştirildi. Her iki tablo birebir aynı alanları taşıyordu — `isInternal` flag'i ile kendi organizasyonu da burada yönetilebilir.

| Alan | Tip | Zorunlu | Açıklama |
|---|---|---|---|
| `tradingPartner` | TradingPartner (ManyToOne) | Evet | `trading_partner_id` FK |
| `certificationType` | CertificationType (ManyToOne) | Evet | `certification_type_id` FK |
| `licenseNo` | String (100) | Hayır | Lisans numarası |
| `issuedAt` | LocalDate | Hayır | Veriliş tarihi |
| `validUntil` | LocalDate | Hayır | Bitiş tarihi |
| `documentRef` | String (255) | Hayır | Doküman referansı |

### Helper Metod

| Metod | Açıklama |
|---|---|
| `isValid()` | `validUntil == null` veya `today ≤ validUntil` ise `true` |

### Snapshot Pattern

WorkOrder oluşturulurken aktif sertifika bilgileri WorkOrder'a **snapshot** olarak kopyalanır — sonraki güncellemelerden etkilenmez.

> **Neden?** GOTS denetiminde "bu iş emrini verdiğinde supplier sertifikalı mıydı?" sorusuna cevap verebilmek için.
> **Detay:** `02-production/work-order.md` — Supplier Snapshot Alanları

---

## İş Kuralları

### Sertifika Süresi Kontrolü

- Sertifika süresi yaklaşırken (30 gün kala) → `SupplierLicenseExpiringSoon` eventi
- FlowBoard'da GENERAL task açılır: "Supplier lisansı yenilenmeli"
- NotificationHub'dan bildirim gönderilir

### Partner Status Geçişleri

```
PENDING → ACTIVE    — onay sürecinden geçtikten sonra
ACTIVE → SUSPENDED  — geçici askıya alma
ACTIVE → BLOCKED    — kalıcı engelleme
SUSPENDED → ACTIVE  — yeniden aktifleştirme
```

---

## İlişki Özeti

```
TradingPartnerRegistry (platform seviyesi golden record)
      ↓
TradingPartner (tenant seviyesi)
  ├──→ PartnerCertification (1:N) ──→ CertificationType
  ├──→ WorkOrder (1:N, tedarikçi olarak)
  ├──→ SalesOrder (1:N, müşteri olarak — customerId)
  ├──→ PurchaseOrder (1:N)
  └──→ SubcontractOrder (1:N)
```

---

## Dış Bağlantılar

| Döküman | İlişki |
|---|---|
| `01-foundations/reference-tables.md` | CertificationType → PartnerCertification |
| `02-production/work-order.md` | WorkOrder.tradingPartnerId + snapshot alanları |
| `03-sales/sales-order.md` | SalesOrder.customerId → TradingPartner |
| `04-procurement/purchase-order.md` | PurchaseOrder.tradingPartnerId |
| `04-procurement/subcontract-order.md` | SubcontractOrder.tradingPartnerId |
| `11-cross-cutting/event-catalog.md` | SupplierLicenseExpiringSoon eventi |

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 2026-03-17 | İlk versiyon — OrganizationCertification birleşmesi belgelendi |

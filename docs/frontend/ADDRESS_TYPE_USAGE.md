# AddressType Kullanım Kılavuzu

## Enum Değerleri

### Kişisel Adres Tipleri (Personal)

- `HOME` - Çalışanın ikamet adresi
- `BILLING` - Faturalama adresi (kişisel/kurumsal)
- `MAILING` - Posta adresi
- `TEMPORARY` - Geçici konaklama adresi
- `ALTERNATE` - İkinci ikamet adresi

### Kurumsal Adres Tipleri (Corporate)

- `OFFICE` - Ofis binası (yeni adresler için kullan)
- `WORK` - Ofis adresi (eski, geriye uyumluluk için)
- `HEADQUARTERS` - Merkez ofis
- `BRANCH` - Şube ofisi
- `WAREHOUSE` - Depo/üretim tesisi
- `FACTORY` - Fabrika adresi
- `SHIPPING` - Sevkiyat adresi
- `BILLING` - Faturalama adresi (kurumsal)

### Saha Adres Tipleri (Field)

- `WORKSITE` - Saha görev adresi (inşaat, proje alanı)
- `REMOTE` - Uzaktan çalışma konumu

## Validation Kuralları

### User Adresleri İçin Geçerli Tipler

```typescript
// Frontend validation
const validForUser = [
  "HOME",
  "BILLING",
  "MAILING",
  "TEMPORARY",
  "ALTERNATE", // Personal
  "OFFICE",
  "WORK", // Office
  "WORKSITE",
  "REMOTE", // Field
];
```

### Company Adresleri İçin Geçerli Tipler

```typescript
// Frontend validation
const validForCompany = [
  "OFFICE",
  "WORK",
  "HEADQUARTERS",
  "BRANCH", // Corporate
  "WAREHOUSE",
  "FACTORY",
  "SHIPPING",
  "BILLING", // Operational
  "WORKSITE",
  "REMOTE", // Field
];
```

## Kullanım Örnekleri

### User Adresi Oluşturma

```typescript
// POST /api/common/users/{userId}/addresses/create-and-assign
{
  "streetAddress": "123 Main St",
  "city": "Istanbul",
  "country": "Turkey",
  "addressType": "HOME",  // ✅ Geçerli
  "label": "Ev Adresi"
}
```

### Company Adresi Oluşturma

```typescript
// POST /api/common/companies/{companyId}/addresses/create-and-assign
{
  "streetAddress": "456 Business Ave",
  "city": "Istanbul",
  "country": "Turkey",
  "addressType": "HEADQUARTERS",  // ✅ Geçerli
  "label": "Merkez Ofis"
}
```

## Kategoriler

Backend'de `AddressTypeCategory` enum'ı ile gruplandırma:

- `PERSONAL` - Kişisel adresler
- `CORPORATE` - Kurumsal adresler
- `FIELD` - Saha adresleri

## Önemli Notlar

1. **WORK vs OFFICE**: Yeni adresler için `OFFICE` kullan, `WORK` eski sistemler için.
2. **BILLING**: Hem kişisel hem kurumsal için kullanılabilir.
3. **Validation**: Backend otomatik kontrol eder, frontend'de de kontrol etmek iyi practice.
4. **Default**: User adresleri için default `"WORK"` (eski sistem uyumluluğu).

## TypeScript Tip Tanımı

```typescript
type AddressType =
  | "HOME"
  | "BILLING"
  | "MAILING"
  | "TEMPORARY"
  | "ALTERNATE" // Personal
  | "OFFICE"
  | "WORK"
  | "HEADQUARTERS"
  | "BRANCH" // Corporate
  | "WAREHOUSE"
  | "FACTORY"
  | "SHIPPING" // Operational
  | "WORKSITE"
  | "REMOTE"; // Field

type AddressTypeCategory = "PERSONAL" | "CORPORATE" | "FIELD";
```

# Company → Organization İsimlendirme Refaktörü

## Özet

Frontend ve backend arasında `companyName`/`companyType` ile `organizationName`/`organizationType` uyumsuzluğu giderildi. Form, validasyon, tipler ve API servisleri `organization` köküne göre güncellendi.

---

## Değiştirilen Kritik Dosyalar

### Frontend

| Dosya | Değişiklik |
|-------|------------|
| `fabric-management-frontend/src/features/auth/types/auth.types.ts` | SelfSignupRequest, OrganizationTypeOption, OnboardingPrefill, LoginErrorResponse |
| `fabric-management-frontend/src/types/auth.ts` | SelfSignupRequest, TenantOnboardingRequest, TenantOnboardingResponse |
| `fabric-management-frontend/src/features/auth/schemas/register.schema.ts` | organizationName, organizationType, Türkçe hata mesajları |
| `fabric-management-frontend/src/features/auth/components/RegisterForm.tsx` | Form alanları, state, label'lar (Organizasyon) |
| `fabric-management-frontend/src/features/auth/services/auth.service.ts` | signup doğrudan geçiş, getTenantOrganizationTypes |
| `fabric-management-frontend/src/shared/constants/api-endpoints.ts` | GET_TENANT_ORGANIZATION_TYPES eklendi |

### Backend (Mevcut Durum)

- `SelfSignupRequest.java`: Zaten `organizationName`, `organizationType` kullanıyor ✓
- `TenantOnboardingResponse.java`: Zaten `organizationId`, `organizationUid`, `organizationName` kullanıyor ✓
- `OnboardingPrefillDto.java`: `organizationName`, `organizationType` ✓

---

## Güncel Kod Blokları

### RegisterForm.tsx (Özet)

- `organizationName`, `organizationType` form alanları
- `organizationTypeOptions`, `FALLBACK_ORGANIZATION_TYPE_OPTIONS`
- Label'lar: "Organizasyon Adı", "Organizasyon Türü", "Organizasyon Bilgileri"
- `authService.getTenantOrganizationTypes()`
- `authService.signup({ organizationName, organizationType, ... })`

### auth.service.ts (Özet)

```typescript
// signup - Doğrudan backend'e gönderim, mapping yok
export const signup = async (data: SelfSignupRequest) => {
  const response = await apiClient.post(AUTH_ENDPOINTS.SIGNUP, data);
  return response.data;
};

// getTenantOrganizationTypes - Yeniden adlandırıldı
export const getTenantOrganizationTypes = async (): Promise<OrganizationTypeOption[]>
```

---

## Adım Adım Uygulama Planı

### 1. Tip Katmanı (types)
- [x] `SelfSignupRequest`: `organizationName`, `organizationType`
- [x] `OrganizationTypeOption` (CompanyTypeOption yerine)
- [x] `TenantOnboardingRequest` / `TenantOnboardingResponse`: organization alanları
- [x] `OnboardingPrefill` (features/auth): organizationName, organizationType
- [x] `OnboardingPrefillDto` (types/auth): `organizationName`/`organizationType`

### 2. Validasyon (Zod)
- [x] `registerSchema`: organizationName, organizationType
- [x] `ORGANIZATION_NAME_PATTERN`, `ORGANIZATION_TYPE_VALUES`
- [x] English error messages

### 3. Form & UI
- [x] RegisterForm: organizationName, organizationType Controller'ları
- [x] Labels: Organization Name, Organization Type, Tax ID
- [x] Bölüm başlıkları: Organizasyon Bilgileri, Yönetici Kullanıcı Bilgileri

### 4. API Servisi
- [x] signup: Manuel mapping kaldırıldı, veri doğrudan gönderiliyor
- [x] getTenantOrganizationTypes (getTenantCompanyTypes yerine)
- [x] AUTH_ENDPOINTS.GET_TENANT_ORGANIZATION_TYPES

### 5. Backend (Opsiyonel)
- [x] `OnboardingPrefillDto.java`: `organizationName`, `organizationType`
- [ ] Swagger/OpenAPI dokümantasyonu güncellemesi
- [ ] API path `/api/common/company-types/tenant` → `/api/common/organization-types/tenant` (breaking change)

### 6. Veritabanı (Opsiyonel)
- [ ] `company_` ön ekli kolonlar varsa migration script
- [ ] Mevcut yapıya sadık kalınıyorsa sadece kod seviyesinde mapping

---

## Tip Güvenliği

- `OrganizationType` (`@/types/enums`) kullanılıyor
- `RegisterFormData` = `z.infer<typeof registerSchema>` → organizationName, organizationType
- `SelfSignupRequest` backend ile uyumlu
- Manuel mapping kaldırıldığı için tip uyumsuzluğu riski azaltıldı

---

## Notlar

- **OnboardingPrefillDto**: Backend `organizationName`/`organizationType` dönüyor. Onboarding formu prefill'den bu alanları okuyor.
- **API path**: `GET /api/common/company-types/tenant` değiştirilmedi; backend path'i korunuyor.
- **UI language**: Register form labels and messages remain in English.

---

## Polisaj (Final Cleanup)

### Global Search Sonucu

- **Auth/Onboarding akışı**: `companyName` ve `companyType` tamamen `organizationName`/`organizationType` ile değiştirildi. Kaynak kodda hayalet referans yok.
- **TradingPartner domain**: `CreateTradingPartnerRequest.companyName` ve `TradingPartner` entity — **farklı domain**. Dış ticaret ortakları (supplier, customer) için "company name" iş terimi geçerli; bu refaktör kapsamı dışında.
- **DB migration templates**: `company_id` referansları legacy migration (Company → TradingPartner) için; dokunulmadı.

### Veritabanı Kolonları

- **Organization entity**: `common_organization` tablosu `name`, `tax_id`, `organization_type` kolonlarını kullanıyor (zaten organization isimlendirmesi).
- **TradingPartner**: Kendi şemasında `company_name` vb. olabilir; bu ayrı bir domain.
- **Gelecek migration**: Eğer başka tablolarda `company_name` kolonu varsa, `organization_name` olarak yeniden adlandırılabilir. Mevcut Organization entity `name` kolonunu kullanıyor.

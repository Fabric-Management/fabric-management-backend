# Code Review Raporu: Onboarding 5 Adım Refaktörü

**Kapsam:** Address/Contact ayrımı, AddressStep/ContactStep, schema/types, constants, OnboardingForm, ReviewStep  
**Tarih:** 2025-03-10  
**Skill:** `.agents/skills/senior-code-review`

---

## Özet

Onboarding sihirbazı 5 adımlı yapıya ve adres/iletişim ayrımına geçirildi. Genel mimari net ve tip güvenli. **Kritik:** Artık kullanılmayan iki bileşen dosyası (CompanyAddressStep, CompanyContactStep) projede duruyor; silinmeli. Orta seviyede: defaultOS türetme mantığı render içinde mutasyon yapıyor, hata yakalamada tip assertion kullanılıyor. Düşük: Yorum/schema numaralandırması tutarlılığı, SectionHeader bileşeninin konumu.

---

## 1. Ölü ve İşlevsiz Kodlar (Dead Code & Cleanup)

### Kritik

- **CompanyAddressStep.tsx ve CompanyContactStep.tsx — artık hiçbir yerde import edilmiyor**
  - **Ne:** OnboardingForm.tsx AddressStep ve ContactStep kullanıyor; CompanyAddressStep ve CompanyContactStep import listesinde yok ve projede başka referans da yok.
  - **Neden:** Eski 4 adımlı yapıdan kalma; yeni 5 adımda AddressStep/ContactStep ile değiştirildi. Dosyalar durdukça karışıklık ve “hangisi kullanılıyor?” sorusu oluşur.
  - **Öneri:** Her iki dosyayı da kalıcı olarak silin.

### Düşük

- **Gereksiz import:** Tüm değiştirilen dosyalarda kullanılmayan import yok; temiz.

---

## 2. Kod Kalitesi ve Temiz Kod (Clean Code & Architecture)

### Orta

- **OnboardingForm.tsx — defaultOS her render’da yeni dizi + mutasyon**
  - **Ne:** `const defaultOS = ["FabricOS"]; if (organizationType && ...) defaultOS.push(...)` component body’de; defaultValues ve reset’te bu dizi kullanılıyor.
  - **Neden:** Render sırasında mutasyon (push) yapılıyor; her render’da yeni `["FabricOS"]` referansı oluşuyor. Davranış doğru (defaultValues ilk mount’ta alındığı için) ama okunabilirlik ve KISS açısından türetme tek yerde ve açık olmalı.
  - **Öneri:** `const defaultOS = useMemo(() => { const os = ["FabricOS"]; if (organizationType && ORGANIZATION_TYPE_OS_SUGGESTIONS[organizationType]) os.push(...ORGANIZATION_TYPE_OS_SUGGESTIONS[organizationType]); return os; }, [organizationType]);` ile tek referans ve açık bağımlılık.

- **onboarding.schema.ts — “Step 3” yorumu**
  - **Ne:** platformModulesSchema için yorum “Step 3: Platform Modules” yazıyor; artık adım 4.
  - **Neden:** Adım numaraları 5 adımlı akışa göre 1–5; yorum yanlış bilgi veriyor.
  - **Öneri:** “Step 4: Platform Modules” veya “Platform Modules (step 4)” olarak güncelleyin.

### Düşük

- **ReviewStep.tsx — SectionHeader bileşeni her render’da yeniden tanımlanıyor**
  - **Ne:** `const SectionHeader = ({ title, stepIndex }) => (...)` ReviewStep fonksiyonu içinde.
  - **Neden:** Küçük bir presentational component; her render’da yeni referans. Performans etkisi minimal ama bileşen dışarıda veya aynı dosyada üstte tanımlanabilir.
  - **Öneri:** Dosyanın üstüne taşıyın veya ayrı bir `SectionHeader.tsx` yapın; tercihen aynı dosyada üstte.

- **DRY:** addressSchema / contactSchema alan isimleri constants’taki `fields` ile aynı; validateStep Object.keys(schema.shape) kullandığı için constants’taki step 2/3 `fields` artık kullanılmıyor. Ya fields kaldırılır (tek kaynak: schema) ya da ileride trigger için kullanılacaksa yorumla belirtilir.

---

## 3. Tip Güvenliği ve Hata Yönetimi (Type Safety & Error Handling)

### Orta

- **OnboardingForm.tsx — onSubmit catch içinde error için type assertion**
  - **Ne:** `const axiosError = error as { response?: { ... } };` ile tip atanıyor; ardından axiosError.response?.status kullanılıyor.
  - **Neden:** `error as` ile herhangi bir hatayı bu şekilde kabul ediyoruz; runtime’da yapı farklı olabilir. Tip güvenliği zayıf.
  - **Öneri:** Type guard fonksiyonu kullanın: `function isAxiosLike(e: unknown): e is { response?: { status?: number; data?: unknown } } { return !!e && typeof e === 'object' && 'response' in e; }` ve `if (isAxiosLike(error)) { const status = error.response?.status; ... }`.

### Düşük

- **contactSchema — companyEmail:** Hem `.email()` hem `.regex(EMAIL_PATTERN)` var; email() zaten format doğruluyor. Tekrara düşmemek için regex’i kaldırabilir veya tek mesajla tek doğrulama bırakılabilir.
- **Edge case:** ReviewStep’te `form.getValues()` bir kez çağrılıyor; adım 5’e dönüldüğünde bileşen yeniden render olduğu için güncel değerler alınıyor. Reaktivite gerekmiyorsa mevcut kullanım kabul edilebilir.

---

## 4. Performans ve Güvenlik (Performance & Security)

### Düşük

- **AddressStep.tsx — initialData useMemo:** Form alanlarına bağımlı; bağımlılık listesi doğru. SmartAddressForm’a gereksiz re-render tetikleyecek yeni referanslar önlenmiş; iyi.
- **OnboardingForm — form.watch():** watchedValues tüm form; draft kaydetmek için kullanılıyor. Debounce 500ms ile yapılıyor; makul.
- **Güvenlik:** Kullanıcı girdisi doğrudan DOM’a verilmiyor; React escape ediyor. Hassas veri loglanmıyor; sadece genel “Onboarding complete error” var.

---

## Özet Tablo

| Önem  | Konu                                               | Dosya / Alan                |
|-------|----------------------------------------------------|-----------------------------|
| Kritik| Kullanılmayan bileşen dosyaları                    | CompanyAddressStep.tsx, CompanyContactStep.tsx |
| Orta  | defaultOS render içinde mutasyon                    | OnboardingForm.tsx         |
| Orta  | platformModulesSchema yorumu “Step 3”               | onboarding.schema.ts       |
| Orta  | Hata tipi için type assertion                      | OnboardingForm.tsx onSubmit |
| Düşük | SectionHeader her render’da yeni referans           | ReviewStep.tsx              |
| Düşük | contactSchema companyEmail çift doğrulama           | onboarding.schema.ts       |
| Düşük | constants step 2/3 fields kullanılmıyor            | constants.ts                |

---

## Düzeltilmiş / Refactor Edilmiş Kod

### 1. Ölü kod temizliği (Kritik)

**Silinecek dosyalar:**

- `fabric-management-frontend/src/features/onboarding/components/CompanyAddressStep.tsx`
- `fabric-management-frontend/src/features/onboarding/components/CompanyContactStep.tsx`

### 2. OnboardingForm.tsx — defaultOS useMemo (Orta)

```ts
// Önce (mevcut):
const organizationType = prefill?.onboardingPrefill?.organizationType;
const defaultOS = ["FabricOS"];
if (organizationType && ORGANIZATION_TYPE_OS_SUGGESTIONS[organizationType]) {
  defaultOS.push(...ORGANIZATION_TYPE_OS_SUGGESTIONS[organizationType]);
}

// Sonra (öneri):
const organizationType = prefill?.onboardingPrefill?.organizationType;
const defaultOS = useMemo(() => {
  const os: string[] = ["FabricOS"];
  if (organizationType && ORGANIZATION_TYPE_OS_SUGGESTIONS[organizationType]) {
    os.push(...ORGANIZATION_TYPE_OS_SUGGESTIONS[organizationType]);
  }
  return os;
}, [organizationType]);
```

### 3. onboarding.schema.ts — yorum güncellemesi (Orta)

```ts
// Önce: "Step 3: Platform Modules"
// Sonra: "Step 4: Platform Modules"
```

### 4. OnboardingForm.tsx — type guard (Orta, isteğe bağlı)

```ts
function isAxiosLike(error: unknown): error is {
  response?: { status?: number; data?: { message?: string; code?: string; errors?: string[]; details?: Record<string, string | string[]> } };
} {
  return typeof error === "object" && error !== null && "response" in error;
}

// catch içinde:
if (isAxiosLike(error)) {
  const status = error.response?.status;
  const responseData = error.response?.data;
  // ...
}
```

Bu rapor, onboarding 5 adım refaktörü kapsamındaki tüm güncellenmiş implementasyonlara senior-code-review skill’i uygulanarak üretilmiştir.

# Code Review Raporu: Onboarding Chat Refactor’ları

**Kapsam:** Bu chat boyunca yapılan tüm güncellemeler (api-endpoints, onboarding schema, OnboardingForm, OnboardingWithPrefill, RegisterForm, constants, OrganizationDetailsStep, ContactStep, ReviewStep, draft parse, beforeunload, leave confirmation).  
**Tarih:** 2025-03-10  
**Skill:** `.agents/skills/senior-code-review`

---

## Özet

Refactor’lar genel olarak tip güvenliği, dil standardizasyonu ve veri kaybı önleme açısından tutarlı. Kritik bulgu yok. Orta seviyede: OnboardingWithPrefill’de prefill için güvenli parse eksikliği (draft ile aynı seviyede değil), OnboardingForm’da validateStep catch’inde sessiz hata. Düşük seviyede: gereksiz çift import, DRAFT_STRING_KEYS ile schema arasında tek kaynak (DRY) eksikliği, empty catch’e isteğe bağlı log.

---

## 1. Ölü ve İşlevsiz Kodlar (Dead Code & Cleanup)

### Düşük
- **RegisterForm.tsx — Çift import:** `InputField` ve `CheckboxField, LoadingButton` iki ayrı satırda `@/ui/components` üzerinden import ediliyor. Tek import satırında birleştirilebilir; sadece okunabilirlik/consistency için.

---

## 2. Kod Kalitesi ve Temiz Kod (Clean Code & Architecture)

### Orta
- **OnboardingForm.tsx — DRAFT_STRING_KEYS vs schema:** Draft için kullanılan string anahtarlar `DRAFT_STRING_KEYS` içinde manuel tutuluyor. Yeni bir form alanı eklendiğinde hem schema hem bu liste güncellenmeli; tek kaynak (schema shape’den türetmek) DRY açısından daha iyi. Şu an için çalışıyor ama ileride unutulma riski var.

### Düşük
- **OnboardingForm.tsx — useEffect dependency yorumu:** `// eslint-disable-next-line react-hooks/exhaustive-deps` kullanılıyor; neden sadece `[prefill]` bırakıldığı (form.reset’in tek seferlik prefill/draft uygulaması) kısa bir yorumla açıklanabilir.

---

## 3. Tip Güvenliği ve Hata Yönetimi (Type Safety & Error Handling)

### Orta
- **OnboardingWithPrefill.tsx — Prefill parse:** `JSON.parse(raw)` try-catch ile korunuyor ancak parse sonrası yapı kontrolü yok. Bozuk veya beklenmeyen bir obje `setPrefill(parsed)` ile state’e girebilir; sonrasında OnboardingForm’da kullanılırken hata veya beklenmedik davranış olabilir. OnboardingForm’daki `parseDraftSafely` benzeri bir “safe parse + shape kontrolü” prefill için de uygulanabilir; en azından `parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)` ve gerekirse bilinen alanların varlığı kontrol edilebilir.

### Düşük
- **OnboardingForm.tsx — validateStep catch:** `catch { return false; }` hata nesnesini kullanmıyor ve loglamıyor. Geliştirme ortamında `console.warn` veya logger ile loglanması debug için faydalı olur; production’da yine false dönmek doğru.
- **OnboardingForm.tsx — parseDraftSafely empty catch:** Hata sessizce yutuluyor, `{}` dönülüyor. Davranış doğru; isteğe bağlı olarak development’ta tek satır log eklenebilir.

---

## 4. Performans ve Güvenlik (Performance & Security)

- **Bellek / listener:** beforeunload listener’ı `hasUnsavedProgress` false olduğunda eklenmiyor ve cleanup’ta kaldırılıyor; doğru.
- **Draft sanitization:** parseDraftSafely ile sadece bilinen string alanlar ve `selectedOS` (string[]) kabul ediliyor; bilinmeyen/alışılmadık veri forma enjekte edilmiyor.
- Kritik performans veya güvenlik bulgusu yok.

---

## Düzeltilmiş / Refactor Önerileri

### 1. OnboardingWithPrefill — Prefill için güvenli parse (Orta)

Prefill’i de draft’e benzer şekilde güvenli parse edin; en azından “plain object” kontrolü ve isteğe bağlı bilinen alan kontrolü:

```ts
function parsePrefillSafely(raw: string | null): OnboardingPrefillPayload | null {
  if (!raw || typeof raw !== "string") return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed))
      return null;
    const obj = parsed as Record<string, unknown>;
    // Minimal shape check for keys we rely on
    if (typeof obj.onboardingPrefill !== "object" || obj.onboardingPrefill === null)
      return null;
    return parsed as OnboardingPrefillPayload;
  } catch {
    return null;
  }
}
```

Ardından `setPrefill(parsePrefillSafely(raw))` kullanın; `raw` varsa doğrudan `parsed as OnboardingPrefillPayload` yerine bu fonksiyonu kullanın.

### 2. OnboardingForm — validateStep catch’e isteğe bağlı log (Düşük)

```ts
} catch (e) {
  if (process.env.NODE_ENV === "development") {
    console.warn("Onboarding step validation failed:", e);
  }
  return false;
}
```

### 3. RegisterForm — Import birleştirme (Düşük)

```ts
import { InputField, CheckboxField, LoadingButton } from "@/ui/components";
```

### 4. DRAFT_STRING_KEYS (Orta) — İleride tek kaynak

Şu an için değişiklik zorunlu değil; ileride schema’dan türetmek için örnek:

- `onboardingSchema.keyof()` veya `Object.keys(onboardingSchema.shape)` gibi bir tek kaynaktan “draft’ta izin verilen string alanlar” listesi türetilebilir; böylece yeni alan eklendiğinde sadece schema güncellenir.

---

## Sonuç

- **Kritik:** 0  
- **Orta:** 2 (prefill safe parse, DRAFT_STRING_KEYS tek kaynak)  
- **Düşük:** 4 (import, useEffect yorumu, validateStep log, parseDraftSafely log)

Önerilen adımlar: Önce OnboardingWithPrefill’de prefill için güvenli parse’ı eklemek, ardından düşük seviye maddeleri (import, yorum, isteğe bağlı log) uygulamak. DRAFT_STRING_KEYS’i schema’dan türetmek ise ayrı bir refactor olarak planlanabilir.

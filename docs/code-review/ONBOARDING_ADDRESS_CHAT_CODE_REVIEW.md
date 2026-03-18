# Code Review Raporu — Onboarding Address & DRY Backend Güncellemeleri

Bu rapor, bu chat kapsamında yapılan güncellemelere **Senior Code Review** skill kriterleri uygulanarak hazırlanmıştır.

**Kapsam:** Onboarding adres label akışı (frontend + backend), backend’de adres oluşturma için ortak DTO kullanımı (DRY), SmartAddressForm’da `hideSaveButton` ile Save butonunun gizlenmesi.

---

## Özet

Değişiklikler genel olarak tutarlı ve DRY prensibine uygun. Kritik bulgu yok. Birkaç orta/düşük seviye iyileştirme: onboarding’de kullanılmayan `onSave` prop’unun anlamlı hale getirilmesi veya dokümante edilmesi, SmartAddressForm’da `onSave` hata durumunda çağıran tarafa iletilmesi (rethrow), backend’de genel `Exception` yakalama yerine daha dar kapsamlı exception kullanımı. Önerilen refactor’lar aşağıda.

---

## 1. Ölü ve İşlevsiz Kodlar

- **[Düşük]** **AddressStep’te `onSave={() => {}}`**: `SmartAddressForm` bileşenine `onSave` zorunlu prop olarak geçiriliyor ancak `hideSaveButton={true}` kullanıldığı için bu callback hiç çağrılmıyor. Teknik olarak ölü değil ama anlamsız bir zorunluluk. **Öneri:** `SmartAddressForm` için `hideSaveButton` true iken `onSave`’in opsiyonel olması (tip: `onSave?: ...`) veya en azından JSDoc’ta “onboarding’de Save gizlendiği için bu callback kullanılmaz” notu eklenmesi.

---

## 2. Kod Kalitesi ve Temiz Kod

- **[Düşük]** **Label boş kontrolü tekrarı**: Backend’de `UserOnboardingService.saveHqAddress` içinde `req.getAddressLabel() != null && !req.getAddressLabel().isBlank()` ile label default’lama yapılıyor; `AddressService.createAddress(CreateAddressRequest)` içinde de benzer `request.getLabel() != null && !request.getLabel().isBlank()` kontrolü var. Aynı “boş/blank → default” mantığı iki yerde. **Öneri:** İleride tekrarı azaltmak için `CreateAddressRequest` için bir `withDefaultLabel(String defaultLabel)` factory veya DTO tarafında null-safe getter kullanılabilir; şu anki hali kabul edilebilir, düşük öncelik.

- **[Düşük]** **AddressStep onChange içinde tekrarlayan setValue**: Dokuz adet `form.setValue(...)` sıralı yazılmış. Okunabilir; ileride alan sayısı artarsa `Object.entries` veya alan listesi + döngü ile tek satırda toplanabilir. Zorunlu değil.

---

## 3. Tip Güvenliği ve Hata Yönetimi

- **[Orta]** **SmartAddressForm — onSave hata yayılımı**: `handleSaveBtn` içinde `await onSave(result.data)` sonrası `catch (err) { console.error(err); }` ile hata yakalanıyor ama **rethrow edilmiyor**. Bu kullanımda Settings’teki `handleSaveAddress` kendi try/catch’inde `toast.error` göstermek için `onSave`’in reject etmesini bekliyor olabilir; hata SmartAddressForm’da yutulduğu için çağıran tarafa ulaşmıyor. **Öneri:** `catch (err) { console.error(err); throw err; }` veya en azından `onSave` reject ettiğinde reject’i tekrar fırlatarak çağıranın catch’inin çalışmasını sağlamak. (Bu davranış bu chat’teki değişiklikten önce de vardı; skill kapsamında iyileştirme olarak not edildi.)

- **[Orta]** **UserOnboardingService.saveHqAddress — genel Exception**: `catch (Exception ex)` ile tüm hatalar yakalanıp sadece `log.warn` yapılıyor. Kasıtlı “adres kaydedilemezse onboarding yine de tamamlansın” tasarımı olabilir; ancak `IllegalArgumentException` veya validasyon hataları da yutuluyor. **Öneri:** En azından belirli exception türlerini (ör. `DataAccessException`) yakalayıp diğerlerini tekrar fırlatmak veya log’a stack trace ekleyip monitoring’de fark ettirmek. Kritik değil ama hata ayıklamayı kolaylaştırır.

- **[Düşük]** **Frontend — addressLabel opsiyonel**: Şemada `addressLabel` optional; `watch("addressLabel")` ve `setValue("addressLabel", addr.label || "")` ile kullanım tutarlı, tip güvenli.

---

## 4. Performans ve Güvenlik

- **N+1 / CORS / memory leak:** Yapılan değişikliklerde yeni N+1 sorgusu, CORS gevşetmesi veya abonelik temizlenmeyen listener yok. Mevcut controller/service akışı aynı kaldı.

- **Güvenlik:** Adres alanları DTO üzerinden geliyor, doğrudan SQL/JPQL’e string birleştirme yok. Label/addressLabel backend’de `@Size(max=100)` ile sınırlı; ek bir sanitization ihtiyacı yok.

---

## Düzeltilmiş / Refactor Edilmiş Kod Önerileri

### 1) SmartAddressForm — onSave hata iletimi (öneri)

```ts
// handleSaveBtn içinde
setIsSaving(true);
try {
  await onSave(result.data);
} catch (err) {
  console.error(err);
  throw err; // veya: throw err; — çağıranın catch'ine ulaşsın
} finally {
  setIsSaving(false);
}
```

### 2) AddressStep — onSave için JSDoc (alternatif; tip değişikliği istemiyorsanız)

```tsx
<SmartAddressForm
  initialData={initialData}
  onChange={...}
  onSave={() => {}}
  /* Save button is hidden (hideSaveButton); data is persisted on Continue. onSave is required by SmartAddressForm but not invoked here. */
  isCancelable={false}
  hideSaveButton
  ...
/>
```

### 3) UserOnboardingService — exception türü (isteğe bağlı)

```java
} catch (DataAccessException | IllegalArgumentException ex) {
  log.warn("saveHqAddress: failed for orgId={} — {}", orgId, ex.getMessage());
} catch (Exception ex) {
  log.warn("saveHqAddress: unexpected error for orgId={} — {}", orgId, ex.getMessage(), ex);
  throw ex; // veya: rethrow for critical path
}
```

---

## Sonuç

Değişiklikler production için uygun; kritik risk yok. Orta seviye öneriler (onSave rethrow, Exception daraltma) isteğe bağlı uygulanabilir; düşük seviye maddeler ileride refactor sırasında ele alınabilir.

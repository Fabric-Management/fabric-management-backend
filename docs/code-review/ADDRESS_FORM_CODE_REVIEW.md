# Code Review: Address Form & Onboarding (buildingNumber Removal)

**Kapsam:** Bu sohbette yapılan tüm güncellemeler  
**Dosyalar:** `AddressFormFields.tsx`, `SmartAddressForm.tsx`, `addressMappers.ts`, `EditAddressDialog.tsx`, `AddressStep.tsx` (onboarding address step; contact in `ContactStep.tsx`)  
**Tarih:** 2025-03-10

---

## 1. Ölü ve İşlevsiz Kodlar (Dead Code & Cleanup)

### Kritik

- **AddressFormFields.tsx — `showDistrict` prop’u kullanılmıyor**
  - **Ne:** `showDistrict: _showDistrict = true` alınıyor ama bileşen içinde `_showDistrict` hiç kullanılmıyor; District/County alanı her zaman render ediliyor.
  - **Neden:** Arayüzde “Show district/county field (used in personal addresses)” deniyor ama davranış buna göre değişmiyor. Bu hem ölü kod hem de yanlış davranış (prop’a güvenen çağıranlar etkilenir).
  - **Öneri:** District alanını `showDistrict` true iken göster, false ise gizle; ya da prop’u kaldırıp JSDoc’u güncelle.

### Orta

- **EditAddressDialog.tsx — Boş `onSubmit` ve sabit `isDirty`**
  - **Ne:** `SmartWizardModal` için `onSubmit={async () => {}}` ve `isDirty={true}` kullanılıyor.
  - **Neden:** Gerçek submit SmartAddressForm’un “Save” ile yapılıyor; modal’ın submit’i no-op. `isDirty` her zaman true, form gerçekten dirty mi bilinmiyor. Anti-pattern ve ileride karışıklık kaynağı.
  - **Öneri:** `onSubmit` için kısa JSDoc ile niyet belirt; mümkünse ileride `isDirty`’yi form state’e bağla.

### Düşük / Tavsiye

- **Gereksiz import:** Tüm dosyalarda kullanılmayan import yok; temiz.
- **Zombie / yorum satırı kod:** Yok.

---

## 2. Kod Kalitesi ve Temiz Kod (Clean Code & Architecture)

### Orta

- **AddressStep.tsx — `initialData` her render’da yeni obje**
  - **Ne:** `initialData={{ streetAddress: form.getValues().addressLine1 || "", ... }}` her render’da yeni referans.
  - **Neden:** SmartAddressForm’daki `useEffect([initialData])` her parent re-render’da tetikleniyor; gereksiz setState ve potansiyel flicker. DRY/KISS açısından da “form values → initialData” tek yerde toplanabilir.
  - **Öneri:** `useMemo` ile form değerlerinden `initialData` üret; bağımlılık olarak sadece ilgili form alanlarını ver (örn. `form.watch("addressLine1", "city", ...)` veya watch’ın döndürdüğü ilgili slice).

- **SmartAddressForm.tsx — `initialData` referansı ile useEffect**
  - **Ne:** `useEffect(() => { ... }, [initialData]);` — parent her render’da yeni obje verirse effect sürekli çalışır.
  - **Neden:** Controlled/uncontrolled karışımı; bazen “sadece ilk açılışta doldur” bazen “her zaman parent’la senkron” beklenebilir. Referans değişince kullanıcı yazarken bile overwrite riski (bu akışta setValue ile senkron olduğu için şu an veri kaybı yok ama gereksiz güncelleme var).
  - **Öneri:** “Sync from parent only when step/mount changes” gibi bir strateji (örn. step/key veya open değişiminde tek seferlik sync) veya initialData’yı shallow compare ile kullan.

- **Single Responsibility:** Adres bileşenleri genel olarak tek sorumlulukta; `handleSearchResult` hem parse hem merge hem setState yapıyor ama tek “address selection” işi sayılır, kabul edilebilir.

### Düşük

- **İsimlendirme:** `_showDistrict` — underscore “bilerek kullanılmıyor” anlamında; prop’u gerçekten kullanınca isim `showDistrict` olmalı.
- **addressMappers.ts:** DRY ve KISS’e uygun; tek sorumluluk net.

---

## 3. Tip Güvenliği ve Hata Yönetimi (Type Safety & Error Handling)

### Orta

- **SmartAddressForm.tsx — `formData as SmartAddressData`**
  - **Ne:** `onSave(formData as SmartAddressData)` ve `handleSaveBtn` içinde required alanlar manuel kontrol ediliyor.
  - **Neden:** `formData` aslında `Partial<SmartAddressData>`; TypeScript’e “tam dolu” diye assertion atıyoruz. Zorunlu alanlar runtime’da kontrol ediliyor ama tip sistemi bunu yansıtmıyor.
  - **Öneri:** Validation’dan geçen değerleri kullanarak tip güvenli bir obje kur (örn. `validateAndBuildPayload(formData)` gibi) ve onu `onSave`’e ver; böylece assertion kalkar.

- **EditAddressDialog.tsx — `as AddressType`**
  - **Ne:** `(data?.addressType ?? "HEADQUARTERS") as AddressType`
  - **Neden:** Backend’den gelen değer AddressType enum’unda olmayabilir; runtime’da hata çıkabilir.
  - **Öneri:** Enum’da olup olmadığını kontrol eden helper kullan veya type guard yaz.

### Düşük

- **AddressFormFields.tsx — `errors?: Record<string, string>`**
  - **Ne:** Hata anahtarları serbest string.
  - **Neden:** `keyof AddressFormData` ile kısıtlanabilir; yanlış key ile hata verme riski azalır.
  - **Öneri:** `errors?: Partial<Record<keyof AddressFormData, string>>` gibi bir tip.

- **Null/undefined:** `|| ""` ve `?? ""` kullanımları tutarlı; edge case’ler makul yönetilmiş.

---

## 4. Performans ve Güvenlik (Performance & Security)

### Orta

- **AddressFormFields.tsx — `getCountryOptions()` her render’da iki kez**
  - **Ne:** `getCountryOptions()` hem SelectField `options` hem de `handleCountrySelect` içinde (find için) çağrılıyor; her seferinde yeni dizi üretiliyor.
  - **Neden:** Gereksiz alloc ve referans değişimi; büyük listelerde ve sık re-render’da gereksiz iş.
  - **Öneri:** `countries.ts`’te `COUNTRY_OPTIONS` gibi modül seviyesinde bir kez hesaplanmış sabit export et; bileşende onu kullan.

### Düşük

- **Re-render:** AddressStep + SmartAddressForm zincirinde initialData’nın her render’da yeni referansı (yukarıda belirtildi) ek re-render ve effect tetikliyor; memo/useMemo ile azaltılabilir.
- **Bellek sızıntısı:** AbortController veya subscription yok; adres formlarında bellek sızıntısı riski yok.
- **Güvenlik:** Kullanıcı girdisi doğrudan DOM’a değil, React state + backend’e gidiyor; XSS için ek bir nokta yok.

---

## Özet Tablo

| Önem       | Konu                                      | Dosya                  |
|-----------|-------------------------------------------|------------------------|
| Kritik    | `showDistrict` prop’u kullanılmıyor       | AddressFormFields.tsx  |
| Orta      | No-op onSubmit, sabit isDirty              | EditAddressDialog.tsx  |
| Orta      | initialData her render yeni referans       | AddressStep.tsx         |
| Orta      | initialData useEffect sürekli tetiklenebilir | SmartAddressForm.tsx  |
| Orta      | formData as SmartAddressData assertion     | SmartAddressForm.tsx   |
| Orta      | getCountryOptions() her render 2x         | AddressFormFields.tsx  |
| Düşük     | errors tipi keyof ile kısıtlanabilir       | AddressFormFields.tsx  |
| Düşük     | AddressType type guard                    | EditAddressDialog.tsx  |

---

## Refactor Öncelikleri

1. **AddressFormFields:** `showDistrict`’i kullan (District alanını koşullu render et).
2. **AddressFormFields:** Ülke listesi için modül seviyesinde sabit kullan; `getCountryOptions()`’ı iki kez çağırma.
3. **AddressStep:** `initialData`’yı `useMemo` ile form değerlerinden türet.
4. **EditAddressDialog:** No-op `onSubmit` ve `isDirty` için kısa açıklama yorumu ekle.
5. **SmartAddressForm:** İleride `initialData` sync stratejisini (mount/step bazlı) netleştir; tip tarafında validation sonrası payload ile assertion’ı kaldır.

Bu rapor, yapılan adres formu ve onboarding değişikliklerinin endüstri standartlarına göre incelenmesini özetler.

---

## Uygulanan Düzeltmeler (Refactor)

Aşağıdaki değişiklikler koda uygulandı:

1. **AddressFormFields.tsx**
   - `showDistrict` artık kullanılıyor: District/County alanı yalnızca `showDistrict === true` iken render ediliyor; grid sütun sayısı da buna göre 2 veya 3 olacak şekilde ayarlandı.
   - `getCountryOptions()` her render’da iki kez çağrılmak yerine modül seviyesinde `COUNTRY_OPTIONS` sabiti kullanılıyor (tek hesaplama, tek referans).

2. **AddressStep.tsx**
   - `initialData` her render’da yeni obje yerine `watch("addressLine1", ...)` ile izlenen değerler ve `useMemo` ile türetilen `initialData` kullanılıyor; referans yalnızca ilgili form alanları değiştiğinde güncelleniyor.

3. **EditAddressDialog.tsx**
   - `SmartWizardModal` için submit ve isDirty davranışını açıklayan kısa bir yorum eklendi.

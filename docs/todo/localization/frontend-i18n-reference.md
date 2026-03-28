# Frontend İçi Backend i18n & Lokalizasyon Referansı

Bu doküman, "Agnostik Backend - Çeviren Frontend" mimarisi kapsamında frontend takımının UI çevirilerini ve hata mesajlarını yönetirken referans alacağı bir özettir.

## 1. Mimari Özet (Agnostik API)
Backend artık kullanıcılara gösterilecek hata mesajlarını İngilizce veya Türkçe olarak statik metin (String) şeklinde dönmez. Bunun yerine, hatanın türünü belirten bir **`errorCode`** ve o hataya ait dinamik değişkenleri (Örn: "X alanı boş olamaz" içindeki X değişkeni) barındıran **`args`** dizisini döner. 
Frontend'in görevi, `errorCode`'u alıp kendi dil dosyasındaki (Örn: `tr.json`, `en.json`) karşılığını bulmak ve varsa `args` dizisindeki elemanları bu şablona yerleştirmektir (String Interpolation/Formatting).

## 2. API Hata Formatı (`ApiErrorResponse`)
Herhangi bir iş kuralı ihlalinde (400 Bad Request, 404 Not Found, 403 Forbidden, 409 Conflict vb.), backend aşağıdaki standart formda bir JSON dönecektir:

```json
{
  "errorCode": "FIBER_COMPOSITION_MAX_EXCEEDED",
  "args": [100.0],
  "timestamp": "2026-03-27T14:00:00Z"
}
```

- **`errorCode`**: Tamamen büyük harf ve modül ismini içerecek şekilde standardize edilmiştir (Screaming Snake Case). Frontend'in çeviri dosyasında bu kod bir key olmalıdır.
- **`args`**: Çeviri şablonundaki değişkenleri sırasıyla temsil eder (Örn: `{0}`, `{1}`). Bazı hatalarda bu dizi boş (`[]`) veya `null` olabilir.

### Frontend Örnek Kullanım (Pseudo-code)
```javascript
// tr.json (Çeviri Dosyası)
{
  "FIBER_COMPOSITION_MAX_EXCEEDED": "Lif oranı %100 değerini aşamaz: %{0}",
  "AUTH_INVALID_CREDENTIALS": "Kullanıcı adı veya şifre hatalı."
}

// Interceptor / Error Handler
const translatedMessage = i18n.t(error.response.data.errorCode, error.response.data.args);
toast.error(translatedMessage);
```

## 3. Kabul Edilen Dil Başlığı (`Accept-Language`)
Frontend, API isteklerinde her zaman aktif dili `Accept-Language` HTTP header'ı ile göndermelidir (Örn: `Accept-Language: tr-TR` veya `en-US`).
Bu başlık backend tarafında şu işlere yarar:
1. Senkron e-posta gönderimleri ve bildirim gövdeleri gibi arkada oluşturulan metinlerin doğru dilde render edilmesi.
2. `MfaSetupService` gibi ara mesaj ("MFA setup initiated") üreten servislerin anlık olarak kullanıcının diliyle yanıt dönmesi.
3. Arka plan işlemleri (Cron job'lar) fallback olarak kullanıcının profiline eklenen `preferred_locale` ayarını baz alır. 

## 4. Uygulanan Modüller ve Referans Hata Kodları Check-listi
Aşağıdaki modüller başarıyla `DomainException` hiyerarşisine geçirilmiş ve standart hata kodları (errorCode) üretecek şekilde refactor edilmiştir:

- [x] **Platform/Auth:**
  - Login, Kayıt, Şifre Sıfırlama, MFA kurulum süreçleri.
  - Örnek `errorCode`: `AUTH_INVALID_CREDENTIALS`, `AUTH_MFA_NOT_INITIATED`, `AUTH_USER_NOT_FOUND`
- [x] **Platform/User:**
  - Kullanıcı oluşturma, rol atama, profil isteği ve kontak doğrulama süreçleri.
  - Örnek `errorCode`: `USER_CREATION_ROLE_NOT_FOUND`, `USER_ADDRESS_ALREADY_ASSIGNED`
- [x] **Platform/Org:**
  - Kurum hiyerarşisi oluşturma, vergi numarası kontrolü, kurum-adres bağlama.
  - Örnek `errorCode`: `ORG_TENANT_CONTEXT_MISSING`, `ORG_PARENT_NOT_FOUND`
- [x] **FlowBoard:**
  - Görev (Task) yönetimi, atamalar, panolar (Board) ve listeleme durumları.
  - Örnek `errorCode`: `FLOW_BOARD_TASK_START_INVALID_STATE`, `FLOW_BOARD_UNAUTHORIZED_ASSIGNMENT`
- [x] **Production (Masterdata & Execution):**
  - Lif (Fiber) formülleri, maksimum oran kontrolleri, Batch (Parti) işlemleri, Kalite Kontrol sertifikaları ve miras (Inheritance) motorları.
  - Örnek `errorCode`: `FIBER_COMPOSITION_TOTAL_NOT_100`, `BATCH_INHERITANCE_SCHEMA_NOT_FOUND`, `BATCH_CERT_COPY_SAME_SOURCE_TARGET`, `FIBER_COMPOSITION_NEGATIVE_PERCENTAGE`
- [x] **Sales:**
  - Satış Numuneleri (Sample) durum takibi.
  - Örnek `errorCode`: `SALES_SAMPLE_INVALID_STATE`
- [x] **Background Jobs & Notifications:**
  - Sertifika süre sonu (`BatchCertificationExpiryCheckJob`) hatırlatıcı bildirimlerinin çok dilli (i18n resource) altyapıya entegrasyonu.
  - Sadece arka plan işlemleri ve özel mesajlar backend'den çevrilmiş olarak gelir; form validation / iş kuralı ihlalleri daima `errorCode` barındırır.

## 5. Sıradaki Adımlar (Frontend Takımı İçin)
1. **Axios/Fetch Interceptor Entegrasyonu:** Backend'den dönen 4xx ve 5xx hatalarında `ApiErrorResponse` modelini (`errorCode` ve `args` yapısını) parse eden küresel bir hata handler'ı yazın.
2. **Dil Dosyalarının (`.json`) Şekillenmesi:** Yukarıdaki modüllere ait spesifik `errorCode` anahtarlarını, projedeki `.json` sözlüklerine doldurun.
3. **Accept-Language:** Projedeki API client katmanına `Accept-Language` global header'ını eklemeyi unutmayın.
4. **Profil API:** `/users/me` güncellemelerinde yeni `preferred_locale` ve `preferred_timezone` parametrelerini birer "User Preference" ayarı olarak arayüze ekleyin.

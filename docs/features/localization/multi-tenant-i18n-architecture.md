# Multi-Tenant Internationalization (i18n) & Localization Architecture

Bu döküman, Fabric Management uygulamasının küresel (Global B2B/SaaS) standartlara uygun olarak nasıl çok dilli (Multi-language) ve çoklu saat dilimli (Multi-timezone) yapıda tasarlanması gerektiğini açıklar. Bu mimari, **Spring Boot (Backend)** ve **Next.js (Frontend)** arasındaki sorumlulukların kesin sınırlarla ayrılması prensibine (Decoupled Architecture) dayanır.

## 1. Temel Prensipler (Core Principles)

Uluslararasılaştırma yapısı 3 temel kural üzerine inşa edilmiştir:

1.  **Agnostik Backend (Sunucu Kuralı):** Backend, gerçekleşen olaylar, başarılı işlemler veya hatalar(exception) sonucunda **ASLA çevrilmiş son kullanıcı metni dönmez**. Sadece işlem kodunu (`errorCode`: `USER_NOT_FOUND`) ve gerekli parametreleri döner.
2.  **Frontend Sunumu (Arayüz Kuralı):** Frontend (Next.js), kullanıcının seçili dilini bilir (`next-intl` ile). Backend'den gelen tüm hata kodlarını anında çevirerek kullanıcıya Türkçe veya İngilizce olarak sunar (Sonner, Toast vb. üzerinden).
3.  **Arka Plan İşlemleri (İstisna):** PDF oluşturma, E-posta gönderimi (Hoşgeldin E-postası, Onay Bekliyor bildirimi vb.) gibi arka planda (Scheduler ile) çalışan işlemler için Backend, ilgili çeviriyi bilmek zorundadır. Bu durumlar için sunucuda `MessageSource` (Resource Bundles) mekanizması kullanılır.

## 2. Hiyerarşik Dil ve Saat Dilimi Çözümlemesi (Cascading Preferences)

Multi-tenant bir yapıda (Firma > Çalışan mimarisi), uygulamanın arayüzünde hangi dilin ve hangi saatin gösterileceği **3 kademeli (3-tier) bir önceliklendirme ile (Override)** hesaplanır:

- **Seviye 1 (Sistem Varsayılanı - En Alt SeviyeFallback):** İngilizce (EN) / UTC Saat Dilimi
- **Seviye 2 (Tenant / Firma Ayarı):** Şirket admininin panelden belirlediği varsayılan dil ve saat. (Örn: `tr-TR` ve `Europe/Istanbul`)
- **Seviye 3 (Kullanıcı Ayarı - En Üst Seviye/Override):** Sisteme giriş yapan şirketin çalışanının kendi profili üzerinden yaptığı özel ayar. (Örn: `en-US` ve `Europe/London`)

**Hesaplama Mantığı (Resolution):**
Uygulama daima "Kullanıcı Ayarı"nı kontrol eder. Eğer kullanıcı ayarı yoksa (null), "Firma Ayarı"na bakar. O da yoksa "Sistem Varsayılanı" kullanılır. Bu yapı, Alman bir firmada çalışan İngiliz bir personelin sistemi İngilizce ve Londra saatinde kullanabilmesini garanti eder.

## 3. Mimari Dağılım ve Uygulama Adımları

### A. Veritabanı (Veri Modelleri)
Kullanıcı ve Tenant tablolarına dil/zaman ayrıştırıcı kolonlar eklenir:
1.  **Tenant Tablosu:** `default_locale` (örn: "tr"), `default_timezone` (örn: "Europe/Istanbul")
2.  **User Tablosu:** `preferred_locale` (örn: "en"), `preferred_timezone` (örn: "America/New_York")

### B. Frontend (Next.js) Sorumlulukları
1.  **Context & Zustand:** Kullanıcı `/api/v1/auth/me` isteği atarak giriş yaptığında dönen User Context içinde tenant ve user localization tercihleri bulunur.
2.  **Kütüphane Entegrasyonu (`next-intl`):** Tüm statik arayüz (UI) metinleri ve Enum yapıları `messages/tr.json` ve `messages/en.json` dosyalarına taşınır.
3.  **Ağ Katmanı (Axios Interceptors):**
    *   **İstek (Request):** Backend'e atılan her Axios isteğine `Accept-Language: tr` (aktif hesaba göre) HTTP başlığı (Header) eklenir.
    *   **Yanıt (Response/Error):** Gelen hata 400 ise, `error.response.data.errorCode` değeri okunup `t('errors.USER_NOT_FOUND')` ile lokal olarak uyarılır.

### C. Backend (Spring Boot) Sorumlulukları
1.  **REST API Hata Yönetimi:** `@ControllerAdvice` içerisine yazılan Global Exception Handler, hata anında mesaj dönmek yerine Standart Error Code mimarisi döner:
    ```json
    {
      "status": 400,
      "errorCode": "error.password.short",
      "args": [8]
    }
    ```
2.  **Arka Plan Bildirimleri (Email, PDF):** REST API'den bağımsız olarak çalışan zamanlanmış görevlerde (Schedulers) Spring Boot `MessageSource` ayağa kalkar. 
    *   İlgili kullanıcının/Tenant'ın Locale ve Timezone bilgileri veritabanından çekilir.
    *   E-posta, doğrudan tercih edilen dile göre `messages_en.properties` vs üzerinden oluşturulur ve gönderilir.

## Sonuç
Bu ayrıştırılmış mimari; backend katmanında kod kirliliğini tamamen önlerken (Çeviri metinleri java koduna sızmaz), frontend tarafında mükemmel bir tip güvenliği (Type Safety) ve son kullanıcı deneyimi sağlar. E-posta ve PDF'ler gibi zorunlu kalınan noktalarda ise tam denetimi Backend üstlenir.

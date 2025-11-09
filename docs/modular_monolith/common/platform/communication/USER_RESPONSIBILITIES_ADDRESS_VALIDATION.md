# 👤 Kullanıcı Sorumlulukları - Address Validation & Standardization

**Tarih:** 2025-11-01  
**Hedef:** Google Maps Platform entegrasyonu için gerekli konfigürasyonlar

---

## 🎯 Genel Bakış

Address Validation ve Standardization özelliği için **Google Maps Platform** entegrasyonu gerekiyor. Bu doküman, sizin (kullanıcının) yapmanız gerekenleri detaylandırır.

---

## ✅ Sizin Sorumluluklarınız

### **1. Google Cloud Platform (GCP) Projesi Oluşturma**

**Adımlar:**

1. [Google Cloud Console](https://console.cloud.google.com/) → Yeni proje oluştur
2. Proje adı: `fabric-management-production` (veya tercih ettiğiniz isim)
3. Billing hesabını aktifleştir (Google Maps API ücretli servis)

**Not:** Billing aktif olmadan API'ler çalışmaz.

---

### **2. Google Maps Platform API Key Oluşturma**

**Adımlar:**

1. Google Cloud Console → **APIs & Services** → **Credentials**
2. **+ CREATE CREDENTIALS** → **API key**
3. API key'i kopyala (örnek: `AIzaSyB...`)

**Önemli:** Bu API key'i `.env` dosyasına ekleyeceksiniz (adım 5'te).

---

### **3. Gerekli API'leri Aktifleştirme**

Şu API'lerin **ENABLED** olması gerekiyor:

| API                        | Amaç                             | Zorunlu mu?     |
| -------------------------- | -------------------------------- | --------------- |
| **Places API**             | Autocomplete suggestions         | ✅ Evet         |
| **Geocoding API**          | Address validation & coordinates | ✅ Evet         |
| **Address Validation API** | Enhanced validation (opsiyonel)  | ⚠️ İsteğe bağlı |

**Adımlar:**

1. Google Cloud Console → **APIs & Services** → **Library**
2. Her API için:
   - Arama yap (örn: "Places API")
   - API'yi seç
   - **ENABLE** butonuna tıkla

**Kontrol:** APIs & Services → Enabled APIs → 3 API görünmeli

---

### **4. API Key Restrictions Yapılandırma** 🔒

**4.1 Region Restriction (Zorunlu)**

API key → **Application restrictions** → **HTTP referrers (web sites)** veya **IP addresses**

**⚠️ IP ADRESİ SÜREKLI DEĞİŞİYORSA:**

**Seçenek 1: Don't restrict key (Development için - GEÇİCİ) ⭐ ÖNERİLEN**

- ✅ Development için en pratik çözüm
- ⚠️ **GÜVENLİK RİSKİ:** API key herkes tarafından kullanılabilir
- ✅ Sadece development için kullan, production'da mutlaka restriction ekle
- ✅ IP değişikliği sorunu yok
- ✅ **ŞU AN YAP:** Application restrictions → "Don't restrict key" seç

**Seçenek 2: HTTP Referrers (Önerilen - Development + Production)**

```
✅ Allowed referrers:
- http://localhost:* (development için)
- http://127.0.0.1:* (development için)
- https://yourdomain.com/* (production için)
- https://*.yourdomain.com/* (subdomain'ler için)
```

- ✅ IP değişikliği sorunu yok
- ✅ Güvenli (sadece belirtilen domain'lerden kullanılabilir)
- ⚠️ Backend'den çağrı yapıyorsan referrer gönderilmeyebilir (browser'dan çağrı yapıyorsan çalışır)

**Seçenek 3: IP Addresses (Production için)**

```
✅ Allowed IP addresses:
- Your backend server IP (production) - SABİT IP
- Localhost (127.0.0.1) - local development için
```

- ✅ En güvenli (sadece belirtilen IP'lerden kullanılabilir)
- ⚠️ IP değişiyorsa her seferinde eklemek gerekir
- ✅ Production sunucu IP'si genelde sabit kalır

**ÖNERİ:**

1. **Development:** "Don't restrict key" veya HTTP referrers (localhost)
2. **Production:** IP addresses (sabit sunucu IP'si) veya HTTP referrers (domain)

**4.2 API Restriction (ÖNEMLİ)**

API key → **API restrictions**

**⚠️ "Don't restrict key" seçtiysen ama hala REQUEST_DENIED alıyorsan:**

1. **API restrictions kontrolü:**

   - "Don't restrict key" seçili olmalı (hem Application hem API restrictions)
   - Eğer "Restrict key" seçiliyse, şu API'lerin **ENABLED** olması gerekir:
     - ✅ **Places API (New)**
     - ✅ **Geocoding API**
   - **Öneri:** Development için "Don't restrict key" seç (her iki yerde de)

2. **Enabled APIs kontrolü:**

   - Google Cloud Console → **APIs & Services** → **Enabled APIs**
   - Şu API'lerin **ENABLED** olması gerekir:
     - ✅ **Places API (New)** - Autocomplete için
     - ✅ **Geocoding API** - Address validation için

3. **Billing kontrolü:**

   - Google Cloud Console → **Billing**
   - Billing hesabı **aktif** olmalı
   - Google Maps API ücretli servis, billing olmadan çalışmaz

4. **Değişikliklerin yayılması:**
   - Restriction değişiklikleri **5 dakika** içinde yayılır
   - API key'i yeniden kontrol et

**✅ Production için:** Mutlaka "Restrict key" seç ve sadece gerekli API'leri etkinleştir:

**Seçilecek API'ler:**

- ✅ Places API (New)
- ✅ Geocoding API
- ✅ Address Validation API (eğer kullanılıyorsa)

**NOT:** "Don't restrict key" seçilmemeli!

**4.3 Region Bias (Backend'de yapılacak)**

Region bias backend tarafında request içinde gönderilecek:

- Europe (TR, GB, DE, FR, IT, ES, vb.)
- Turkey (TR)
- United Kingdom (GB)

---

### **5. Environment Variable Yapılandırması**

**`.env` dosyasına ekle:**

```bash
# Google Maps Platform API Key
GOOGLE_MAPS_API_KEY=AIzaSyB...your-api-key-here
```

**Önemli:**

- ✅ API key'i asla git repository'ye commit etme!
- ✅ `.gitignore` içinde `.env` dosyası olmalı
- ✅ Production'da secure secret management kullan (AWS Secrets Manager, Azure Key Vault, vb.)

---

### **6. Quota Monitoring (Opsiyonel ama Önerilen)**

Google Cloud Console → **APIs & Services** → **Dashboard**

**İzlenecek metrikler:**

- Günlük request sayısı
- Quota limitleri
- Billing alerts

**Öneri:**

- Günlük quota limiti koy (örnek: 10,000 requests/day)
- Billing alert'i aktif et (örnek: $100/month threshold)

---

## 📋 Checklist

**Sizin tamamlamanız gerekenler:**

- [ ] GCP projesi oluşturuldu
- [ ] Billing hesabı aktif
- [ ] API key oluşturuldu
- [ ] Places API enabled
- [ ] Geocoding API enabled
- [ ] Address Validation API enabled (opsiyonel)
- [ ] API key'e region restriction uygulandı
- [ ] API key'e API restriction uygulandı
- [ ] `GOOGLE_MAPS_API_KEY` environment variable set edildi
- [ ] `.env` dosyası `.gitignore`'da

---

## 🔧 Backend'de Yapılacaklar (Benim Sorumluluğum)

Siz yukarıdaki adımları tamamladıktan sonra, ben şunları yapacağım:

1. ✅ **GoogleMapsClient** service oluşturma
2. ✅ **AddressValidationService** oluşturma
3. ✅ **Address** entity'ye şu field'ları ekleme:
   - `countryCode` (ISO 3166-1 alpha-2)
   - `district` / `county`
   - `latitude` / `longitude`
   - `placeId` (Google Places ID)
   - `formattedAddress` (zaten var, güncelleme)
4. ✅ **AddressService**'e validation metodları ekleme
5. ✅ **AddressController**'a validation endpoint'leri ekleme
6. ✅ Migration script (entity güncellemeleri için)
7. ✅ Error handling & logging

---

## 📝 Örnek API Key Formatı

```bash
# .env dosyasında
GOOGLE_MAPS_API_KEY=AIzaSyB1234567890abcdefghijklmnopqrstuvwxyz
```

**Test:**

```bash
# Terminal'de test (API key'in çalışıp çalışmadığını kontrol et)
curl "https://maps.googleapis.com/maps/api/geocode/json?address=Istanbul,Turkey&key=$GOOGLE_MAPS_API_KEY"
```

---

## ⚠️ Güvenlik Notları

1. **API Key Güvenliği:**

   - ✅ Production'da environment variable kullan
   - ✅ Asla hardcode etme
   - ✅ Git'e commit etme
   - ✅ Log'lara yazdırma

2. **Rate Limiting:**

   - Google Maps API ücretli servis
   - Quota limitleri koy
   - Monitoring aktif et

3. **CORS:**
   - Frontend'den direkt API çağrısı yapılmamalı
   - Tüm çağrılar backend üzerinden yapılmalı

---

## 📞 Destek

Eğer API key oluşturma veya yapılandırma sırasında sorun yaşarsanız:

1. [Google Maps Platform Documentation](https://developers.google.com/maps/documentation)
2. [Google Cloud Console Help](https://cloud.google.com/docs)

---

**Hazır olduğunuzda:** Bana `GOOGLE_MAPS_API_KEY` değerini verin, ben entegrasyonu tamamlayayım! 🚀

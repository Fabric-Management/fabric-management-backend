🧩 Backend Integration Guide — Address Validation & Standardization

Amaç:
Uygulamanın adres yönetimi, doğrulama ve standardizasyon süreçlerinde Google Maps Platform servislerinin (Places API & Geocoding API) backend entegrasyonunu yönlendirmek.

Env değişkeni:

GOOGLE_MAPS_API_KEY=your-secure-api-key-here


Bu anahtar environment variable olarak kullanılmalı,
kesinlikle kodun içine gömülmemeli veya commit edilmemelidir.

🎯 Genel Yaklaşım

Backend tarafı, kullanıcı veya şirket adreslerinin doğrulanması, standardize edilmesi ve veritabanına kaydedilmesinden sorumludur.
Bu işlem sırasında Google Maps Platform servisleri ile güvenli bir biçimde iletişim kurulur.

⚙️ Temel Sorumluluklar
1️⃣ Address Validation Service

Kullanıcılardan gelen adres verilerini Google Geocoding API üzerinden doğrular.

Geri dönen yanıtı normalized formda döner (formatted address, coordinates, postal code, country code).

Yanıta göre adresin doğrulama durumunu belirler (VERIFIED, PARTIAL, FAILED).

2️⃣ Autocomplete (Prediction) Endpoint

Frontend’den gelen yazım sırasında tahmin isteklerini Places API (New) üzerinden yönetir.

Gelen tahminleri sadeleştirip frontend’e döner (ör. description, place_id).

Seçilen adresin place_id bilgisi üzerinden tam doğrulama yapılır.

3️⃣ Address Normalization

Google yanıtındaki address_components yapısını sistemin Address Entity yapısına uyarlar.

Gerektiğinde countryCode, postalCode, latitude, longitude, formattedAddress alanlarını doldurur.

4️⃣ Data Persistence

Yalnızca doğrulama sonucu “VALID” olan adresler kayıt altına alınır.

Adreslerin “country-aware” şekilde (ISO-3166 country code) saklanması zorunludur.

5️⃣ Error Handling & Logging

Geocoding veya Places API hata dönerse kullanıcıya sade bir hata mesajı verilir.

Detaylı hata log’ları backend’de tutulur (ör. quota exceeded, invalid request).

6️⃣ Caching

Aynı adresler tekrar doğrulanmak istendiğinde cache mekanizması (ör. Redis veya local DB) devreye girebilir.

Bu sayede Google API çağrıları azaltılır ve performans artar.

🧭 Ülke Bazlı Davranış Mantığı
Ülke	Davranış
🇬🇧 United Kingdom	“Postcode-first” deneyimi. Kullanıcı postcode girer, backend Places API’den öneriler döner. Seçilen adres Geocoding API ile doğrulanır.
🇩🇪 🇪🇸 🇹🇷 🇫🇷 vs.	“Search-first” deneyimi. Kullanıcı adres yazmaya başlar, öneriler gelir. Seçilen adres backend’de doğrulanır.
🌍 Diğer ülkeler	Genel autocomplete + doğrulama akışı uygulanır, özel kurala gerek yok.

Backend servisleri, ülke fark etmeksizin tek API yapısı ile çalışır.
Google Maps, yerel adres formatına göre veriyi otomatik olarak standardize eder.

🔐 Güvenlik

Tüm Google API istekleri backend’den yapılır (frontend doğrudan erişmez).

API key sadece backend ortamında tanımlanır (.env veya secret manager).

Rate limit ve quota yönetimi aktif olarak izlenmelidir.

Gerektiğinde caching veya throttling uygulanabilir.

🧾 Beklenen Çıktı Formatı (Response Standardı)

Backend servisleri frontend’e aşağıdaki yapıda normalize bir yanıt döner:

Alan	Açıklama
formattedAddress	Google’ın standardize ettiği adres formatı
latitude, longitude	Coğrafi koordinatlar
countryCode	ISO 3166-1 alpha-2 formatında ülke kodu
postalCode	Doğrulanmış posta kodu
verificationStatus	VERIFIED, PARTIAL, veya FAILED
placeId	Google Places ID
formattedAddressShort	UI için kısaltılmış versiyon (isteğe bağlı)
📊 Genel Akış Diyagramı (Yüksek Seviye)

1️⃣ Kullanıcı adres yazar → frontend autocomplete istek atar
2️⃣ Backend → Places API (New) üzerinden önerileri alır
3️⃣ Kullanıcı öneri seçer → place_id backend’e gönderilir
4️⃣ Backend → Geocoding API ile doğrulama yapar
5️⃣ Backend → Normalize edilmiş adresi döner ve kaydeder

✅ Özet

Backend ekibinin hedefi:

“Tek bir API key, tek bir servis yapısı, çoklu ülke desteği.”

Yani:

Ülke bazlı farklılaştırma yok

Adres doğrulama & autocomplete tek serviste yönetiliyor

Her adres doğrulanmadan kayıt edilmiyor

Tüm sonuçlar standardize biçimde dönüyor
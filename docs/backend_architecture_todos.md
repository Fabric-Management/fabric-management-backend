# Backend Architecture Enhancement TODOs

Bu tablo analizi yapılan backend yapısını (Multi-Tenant, AuthUser, TradingPartner) daha esnek, güvenli ve Endüstri (B2B SaaS / ERP) standartlarına tam uyumlu hale getirecek görevleri öncelik sırasına göre listeler.

## 🔴 Öncelik 1: Yüksek (Güvenlik ve Çekirdek İş Esnekliği)

### 1. Multi-Factor Authentication (MFA / 2FA) ve Doğrulama (Routing) Entegrasyonu
- [ ] **MFA İlk Altyapısının Kurulması:** `AuthUser` tablosuna `is_mfa_enabled`, `primary_mfa_type` (Enum: TOTP, EMAIL, SMS, WHATSAPP) ve `mfa_secret` alanları eklenecek.
- [ ] **TOTP (Birincil Gösterim):** `dev.samstevens.totp` kütüphanesi ile bağımsız ve güvenli olan Authenticator (QR Kod) entegrasyonu sağlanacak. Bu yöntem ısrarla teşvik edilecek.
- [ ] **Pazar Bazlı (Market-Based) İletişim Yönlendirmesi:** Tenant'ın bulunduğu pazara göre (örn: Türkiye -> WhatsApp first, UK -> Email first) karar verecek ve koda dokunmadan değiştirilebilecek veritabanı tabanlı bir `Routing Rule` motoru yazılacak.
- [ ] **Asenkron Outbox & Meta Cloud API:** WhatsApp istekleri için aracı kurum kullanılmayacak, doğrudan Meta API entegre edilecek (`Spring Cloud OpenFeign` ile). İstekler doğrudan dış API'ye gitmeyecek, önce "Outbox Pattern" ile `PENDING` statüsünde bekleme/gönderim sırasına alınacak.
- [ ] **Timeout Tabanlı İzleme & SMS Fallback:** `JobRunr` veya benzeri bir zamanlayıcıyla, Meta API'ye gönderilen istekten sonra 15-20 saniye içinde "Delivered" webhook'u gelmezse (internet çekmeme durumu) "Timeout" devreye sokulup otomatik olarak SMS yedek kanalına (Fallback) geçilecek.
- [ ] **Trusted Device (Kayıtlı Cihazlar):** İleriki faz için "Bu cihazda 30 gün boyunca bir daha sorma" Session/Token mantığı kurgulanacak.

### 2. Multi-Organization ve Platform Yönetimi (Matrix Management & WABA)
- [ ] **Personel Matris (Matrix) Yetkilendirmesi:** `User` entitisindeki statik ve tekil `organizationId` alanı geliştirilerek, bir yöneticinin aynı anda birden fazla alt fabrikada/departmanda yetkili olabilmesini sağlayan çoklu (`UserOrganization` vb.) bağlantı kurgulanacak.
- [ ] **Platform Düzeyi Tekil WABA Havuzu:** Her tenant/müşteri için Meta süreci yaşamak yerine, platform tek bir kurumsal WhatsApp Business API (WABA) hesabı üzerinden tüm tenantların doğrulama mesajlarını atacak. İşletme masrafları abonelik kurgusunda hesaplanacak.
- [ ] **Encapsulation (Gizleme):** PazarRouting, Outbox atımı vb. karmaşık operasyonlar çağıran servisten (Controller) gizli tutulacak. Sadece `verificationService.sendOtp(userId)` komutu çağrılarak tamamen Clean Architecture (Solid) kuralları işletilecek.

---

## 🟡 Öncelik 2: Orta (İzlenebilirlik, Oturum Güvenliği ve Doğrulama)

### 3. Oturum (Session) ve Cihaz Yönetimi
- [ ] Sadece "son giriş zamanı" yerine, `UserSession` (veya `RefreshToken` tracking) tablosu tasarlanacak.
- [ ] Refresh Token'lar veritabanında tutulacak ve Cihaz Bilgisi (User-Agent), IP adresi kayıt altına alınacak.
- [ ] Kullanıcı "Tüm oturumlarımı (veya o cihazı) kapat" dediğinde token/session invalidate edilecek (Revoke All Sessions).

### 4. Alan Bazlı Audit Log (Veri İz İzi)
- [ ] `Hibernate Envers` kütüphanesi veya "Event / AOP" tabanlı Custom Audit mekanizması entegre edilecek.
- [ ] Özellikle `User`, `Role`, `TradingPartner` verilerinde kimin, hangi alanı, eski değerden yeni değere ne zaman güncellediği tam loglanacak.
- [ ] (Örn: "Yetkili, X kullanıcısının maaş bilgisini / departmanını güncelledi.")

### 5. Askıda Kalan Onay (Pending Verification) Akışları
- [ ] E-Posta veya Telefon numarası değişikliklerinde anında update yerine, "Bekleyen Değişiklik" (`PendingContactChange`) mantığı kurulacak.
- [ ] Kullanıcı yeni e-postasını onaylayana kadar eski iletişim kanalı "Verified" olarak sistemde kalacak, hesap güvenliği sarsılmayacak.

---

## 🟢 Öncelik 3: Düşük (Kurumsal Uyumluluk ve Delegasyon)

### 6. Şifre Geçmişi ve Kuralları (Password Policies)
- [ ] `PasswordHistory` tablosu oluşturulup, kullanıcının son kullandığı N adet (örn: 5) şifrenin hash'i tutulacak.
- [ ] Kullanıcı şifre sıfırlarken, geçmişteki şifreleri tekrar kullanması validasyon ile engellenecek.
- [ ] `TenantSettings` kısmına "Şifre 90 günde bir yenilenmeli" gibi isteğe bağlı Müşteri kuralları (Policies) tanımlanacak.

### 7. Çapraz / Rol Bazlı Vekalet (Impersonation & Delegation)
- [ ] Müşteri hizmetleri / Destek mühendisleri için "Müşteri gözünden giriş yapma" (`Act_As` veya `Impersonate`) mekanizması kurulacak.
- [ ] Bu girişlerin Audit Loglarında *"Ahmet kullanıcısı işlemi yaptı (Vekaleten: Destek Elemanı Mehmet)"* şeklinde net iz bırakması sağlanacak.

### 8. Cascade Soft-Deletes ve Veri Tutarlılığı Güvencesi
- [ ] Bir `Tenant` (Şirket/Müşteri) üyeliğini kapattığında veya dondurulduğunda (`isActive=false`), altındaki tüm Kullanıcı (User) ve Auth (AuthUser) verilerinin dolaylı olarak deaktif edilmesi garanti altına alınacak (Login engeli).
- [ ] Gerekli Controller Filter veya Entity Listener seviyelerinde `Tenant.isActive` durum kontrollü bariyerler eklenecek.

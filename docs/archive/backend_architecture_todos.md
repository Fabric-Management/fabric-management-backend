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
- [ ] **Personel Matris (Matrix) Yetkilendirmesi *(Donduruldu / İleri Faz)*:** "Soft-Matrix" (Additive) stratesiyle ilerlenecek. Mevcut `User.organizationId` kamerasını kırmadan, `UserOrganization` junction table eklenip (Faz 1), Dual-Read Pattern (Faz 2) ve aşamalı geçiş (Faz 3) ile implement edilecek. Şu anki müşteri profilinde acil ihtiyaç olmadığı için ertelendi.
- [x] **Platform Düzeyi Tekil WABA Havuzu:** Her tenant/müşteri için Meta süreci yaşamak yerine, platform tek bir kurumsal WhatsApp Business API (WABA) hesabı üzerinden tüm tenantların doğrulama mesajlarını atacak. `application.yml` içindeki `whatsapp.*` bloku platform-geneli olarak kurgulanmış, `WhatsAppClient` tek merkezden bağlanıyor.
- [x] **Encapsulation (Gizleme) — Tamamlandı:** `VerificationCodeManager.issueMfaCode(userId, tenantId, mfaType)` ve `validateMfaCode(userId, tenantId, mfaType, code)` facade metodları eklendi. `LoginService` artık contact çözümlemeyi, `VerificationType` belirlemeyi veya iletişim kanalını bilmiyor; tek satırla `verificationCodeManager.issueMfaCode(userId, tenantId, mfaType)` çağrısı yapıyor. İletişim kanalı seçimi (WhatsApp → SMS fallback, Market routing) ve PII maskeleme `VerificationCodeManager` içinde encapsulate edildi. `LoginResponse`'a `maskedContact` alanı eklenerek frontend "j***@gmail.com adresinize kod gönderdik" uyarısını gösterebilir hale getirildi.

---

## 🟡 Öncelik 2: Orta (İzlenebilirlik, Oturum Güvenliği ve Doğrulama)

### 3. Oturum (Session) ve Cihaz Yönetimi — *Tamamlandı*
- [x] Sadece "son giriş zamanı" yerine, `UserSession` (veya `RefreshToken` tracking) tablosu tasarlanacak. *(RefreshToken üzerine ip_address, user_agent, device_name eklenerek RefreshToken bir session tracker olarak genişletildi)*
- [x] Refresh Token'lar veritabanında tutulacak ve Cihaz Bilgisi (User-Agent), IP adresi kayıt altına alınacak. *(DeviceInfoUtil oluşturuldu, ActiveSessionDto yazıldı. RefreshTokenCleanupJob ile eski token temizliği 6 saatte bir CRON ile sağlandı)*
- [x] Kullanıcı "Tüm oturumlarımı (veya o cihazı) kapat" dediğinde token/session invalidate edilecek (Revoke All Sessions). *(LogoutService içine getActiveSessions, revokeSession, logoutFromAllDevices metodları eklendi, AuthController'da GET/DELETE endpointleri açıldı)*

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

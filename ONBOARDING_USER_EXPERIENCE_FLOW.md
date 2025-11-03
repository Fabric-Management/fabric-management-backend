# 🎯 Onboarding Kullanıcı Deneyimi Akışı

## 📋 İÇİNDEKİLER

1. [Genel Bakış](#genel-bakış)
2. [Self-Service Signup Flow](#self-service-signup-flow)
3. [Sales-Led Signup Flow](#sales-led-signup-flow)
4. [Detaylı Adımlar](#detaylı-adımlar)
5. [Frontend Ekranları](#frontend-ekranları)
6. [API Call Sequence](#api-call-sequence)
7. [Hata Senaryoları](#hata-senaryoları)

---

## 🎯 GENEL BAKIŞ

### **İki Farklı Akış:**

```
┌─────────────────────────────────────────────────────────┐
│                    KAYIT SEÇENEKLERİ                      │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  1️⃣  SELF-SERVICE SIGNUP                                 │
│      └─ Website'den kayıt                                 │
│      └─ Email doğrulama                                   │
│      └─ 14 gün trial                                     │
│                                                           │
│  2️⃣  SALES-LED SIGNUP                                   │
│      └─ Satış ekibi oluşturur                            │
│      └─ Token ile doğrulama                              │
│      └─ 90 gün trial                                     │
│                                                           │
└─────────────────────────────────────────────────────────┘
           ↓
    PASSWORD SETUP
           ↓
    ONBOARDING WIZARD
           ↓
       DASHBOARD
```

---

## 🌐 SELF-SERVICE SIGNUP FLOW

### **Adım Adım Kullanıcı Deneyimi:**

```
┌──────────────────────────────────────────────────────────┐
│  STEP 1: WEBSITE VISIT                                    │
│  ──────────────────────────────────────────────────────  │
│  Kullanıcı website'i ziyaret ediyor                       │
│  fabricmanagement.com → "Hemen Başla" butonu             │
│                                                           │
│  [👉 Signup Sayfasına Yönlendir]                         │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 2: SIGNUP FORM (PUBLIC ENDPOINT)                   │
│  ──────────────────────────────────────────────────────  │
│  📝 Kayıt Formu                                           │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Şirket Bilgileri                     │                │
│  ├─────────────────────────────────────┤                │
│  │ Şirket Adı: [________________]      │                │
│  │ Vergi No:   [________________]      │                │
│  │ Şirket Tipi: [Dropdown ▼]         │                │
│  │   • İplikçi (SPINNER)              │                │
│  │   • Dokumacı (WEAVER)              │                │
│  │   • Örücü (KNITTER)                │                │
│  │   • ...                             │                │
│  └─────────────────────────────────────┘                │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Kişisel Bilgiler                     │                │
│  ├─────────────────────────────────────┤                │
│  │ Ad:       [________________]        │                │
│  │ Soyad:    [________________]        │                │
│  │ Email:    [________________]        │                │
│  └─────────────────────────────────────┘                │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ OS Seçimi (Opsiyonel)               │                │
│  ├─────────────────────────────────────┤                │
│  │ ☑ FabricOS (Zorunlu - otomatik)    │                │
│  │ ☐ YarnOS                           │                │
│  │ ☐ LoomOS                           │                │
│  │ ☐ KnitOS                           │                │
│  └─────────────────────────────────────┘                │
│                                                           │
│  ☑ Kullanım şartlarını okudum ve kabul ediyorum         │
│                                                           │
│  [Kayıt Ol]                                              │
│                                                           │
│  API: POST /api/public/signup                            │
│  Request: {                                              │
│    companyName: "ABC Tekstil",                           │
│    taxId: "1234567890",                                  │
│    companyType: "WEAVER",                               │
│    firstName: "Ahmet",                                   │
│    lastName: "Yılmaz",                                   │
│    email: "ahmet@abctekstil.com",                        │
│    selectedOS: ["LoomOS", "FabricOS"],                  │
│    acceptedTerms: true                                   │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 3: SUCCESS MESSAGE                                  │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  ✅ Kayıt Başarılı!                                       │
│                                                           │
│  📧 Size bir doğrulama email'i gönderdik.                │
│  Email'inizi kontrol edin ve şifrenizi oluşturun.         │
│                                                           │
│  [Email'imi Kontrol Et]                                  │
│                                                           │
│  📬 ahmet@abctekstil.com adresine email gönderildi.       │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 4: EMAIL (Kullanıcının Email Kutusu)               │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  ┌──────────────────────────────────────┐               │
│  │ 📧 FabricOS - Hesabınızı Aktifleştirin│              │
│  ├──────────────────────────────────────┤               │
│  │                                       │               │
│  │  Merhaba Ahmet,                       │               │
│  │                                       │               │
│  │  ABC Tekstil için FabricOS hesabınız │               │
│  │  hazır! Şifrenizi oluşturarak        │               │
│  │  hemen başlayabilirsiniz.             │               │
│  │                                       │               │
│  │  ✅ Aktif OS'leriniz:                 │               │
│  │     • FabricOS (Base Platform)       │               │
│  │     • LoomOS (Weaving Production)    │               │
│  │                                       │               │
│  │  🎁 14 Gün Ücretsiz Deneme            │               │
│  │                                       │               │
│  │  [Şifremi Oluştur]                    │               │
│  │  https://app.fabricmanagement.com/    │               │
│  │  setup?token=abc-123-xyz-789          │               │
│  │                                       │               │
│  │  Bu bağlantı 24 saat geçerlidir.      │               │
│  │                                       │               │
│  └──────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 5: PASSWORD SETUP PAGE                             │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  URL: /setup?token=abc-123-xyz-789                        │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Şifrenizi Oluşturun                  │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  Hoş geldiniz! ABC Tekstil           │               │
│  │                                       │               │
│  │  Email:                              │               │
│  │  ahmet@abctekstil.com ✅              │               │
│  │  (Otomatik dolduruldu, doğrulandı)   │               │
│  │                                       │               │
│  │  Şifre:                              │               │
│  │  [••••••••••]                        │               │
│  │  Şifre güçlendirme göstergesi:       │               │
│  │  [████████░░] Güçlü                  │               │
│  │                                       │               │
│  │  Şifre Tekrar:                       │               │
│  │  [••••••••••]                        │               │
│  │                                       │               │
│  │  [Hesabı Aktifleştir]                │               │
│  │                                       │               │
│  │  Önerilen Şifre:                     │               │
│  │  [mK8#pL2$nQ9wR5tX] [Kullan]        │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
│                                                           │
│  API: POST /api/auth/setup-password                      │
│  Request: {                                              │
│    token: "abc-123-xyz-789",                              │
│    password: "SecurePass123!"                            │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 6: AUTO-LOGIN + REDIRECT                           │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  ✅ Şifre başarıyla oluşturuldu!                          │
│                                                           │
│  Response: {                                              │
│    accessToken: "eyJhbGciOiJIUzI1NiIs...",               │
│    refreshToken: "refresh-uuid",                          │
│    user: {                                                │
│      firstName: "Ahmet",                                  │
│      lastName: "Yılmaz",                                 │
│      hasCompletedOnboarding: false,  // ⚠️                │
│      onboardingCompletedAt: null                          │
│    },                                                     │
│    needsOnboarding: true  // ⚠️ Wizard'a yönlendir      │
│  }                                                       │
│                                                           │
│  Frontend Logic:                                          │
│  if (response.needsOnboarding) {                         │
│    navigate("/onboarding");  // ⚠️ Wizard'a git          │
│  } else {                                                 │
│    navigate("/dashboard");                                 │
│  }                                                       │
│                                                           │
│  [👉 Onboarding Wizard'a Yönlendir]                      │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 7: ONBOARDING WIZARD                                │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  URL: /onboarding                                         │
│                                                           │
│  Progress: [████░░░░░░] Step 1/4                         │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Step 1/4: Şirket Profilinizi        │                │
│  │         Tamamlayın                  │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  Adres Bilgileri:                    │               │
│  │  ┌─────────────────────────────────┐ │               │
│  │  │ Sokak/Adres:                   │ │               │
│  │  │ [Organize Sanayi Bölgesi...]  │ │               │
│  │  │                                │ │               │
│  │  │ Şehir:                         │ │               │
│  │  │ [İstanbul ▼]                   │ │               │
│  │  │                                │ │               │
│  │  │ İlçe:                          │ │               │
│  │  │ [İstanbul ▼]                   │ │               │
│  │  │                                │ │               │
│  │  │ Posta Kodu:                    │ │               │
│  │  │ [34000]                        │ │               │
│  │  │                                │ │               │
│  │  │ Ülke:                          │ │               │
│  │  │ [Türkiye ▼]                    │ │               │
│  │  └─────────────────────────────────┘ │               │
│  │                                       │               │
│  │  İletişim Bilgileri:                 │               │
│  │  ┌─────────────────────────────────┐ │               │
│  │  │ Telefon:                        │ │               │
│  │  │ [+90 555 123 45 67]            │ │               │
│  │  │                                │ │               │
│  │  │ Şirket Email:                  │ │               │
│  │  │ [info@abctekstil.com]          │ │               │
│  │  │                                │ │               │
│  │  │ Website (Opsiyonel):           │ │               │
│  │  │ [https://abctekstil.com]       │ │               │
│  │  └─────────────────────────────────┘ │               │
│  │                                       │               │
│  │  [⏭ Skip]  [Next →]                 │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
│                                                           │
│  API: PUT /api/common/companies/me/profile               │
│  Request: {                                              │
│    address: {                                            │
│      streetAddress: "Organize Sanayi...",                 │
│      city: "İstanbul",                                   │
│      state: "İstanbul",                                  │
│      postalCode: "34000",                                 │
│      country: "Türkiye"                                  │
│    },                                                    │
│    phoneNumber: "+905551234567",                         │
│    companyEmail: "info@abctekstil.com",                 │
│    website: "https://abctekstil.com"                     │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 8: ONBOARDING WIZARD - STEP 2                      │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  Progress: [████████░░] Step 2/4                         │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Step 2/4: Departmanlarınızı          │                │
│  │         Oluşturun                    │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  Organizasyon yapınızı kurun:        │               │
│  │                                       │               │
│  │  ┌─────────────────────────────────┐ │               │
│  │  │ Departman Adı:                │ │               │
│  │  │ [Üretim ▼]                    │ │               │
│  │  │                                │ │               │
│  │  │ Açıklama:                     │ │               │
│  │  │ [Üretim planlama ve takibi]   │ │               │
│  │  │                                │ │               │
│  │  │ [Departman Ekle]               │ │               │
│  │  └─────────────────────────────────┘ │               │
│  │                                       │               │
│  │  Eklenen Departmanlar:               │               │
│  │  ✓ Üretim                           │               │
│  │  ✓ Planlama                         │               │
│  │  ✓ Kalite Kontrol                   │               │
│  │                                       │               │
│  │  [⏭ Skip]  [Next →]                 │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
│                                                           │
│  API: POST /api/common/departments                        │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 9: ONBOARDING WIZARD - STEP 3                      │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  Progress: [████████████░░] Step 3/4                     │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Step 3/4: Ekip Üyelerinizi          │                │
│  │         Davet Edin                  │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  Ekip üyelerinizi ekleyerek          │               │
│  │  işbirliğine başlayın:                │               │
│  │                                       │               │
│  │  ┌─────────────────────────────────┐ │               │
│  │  │ Email:                          │ │               │
│  │  │ [mehmet@abctekstil.com]         │ │               │
│  │  │                                │ │               │
│  │  │ Rol:                            │ │               │
│  │  │ [Manager ▼]                    │ │               │
│  │  │                                │ │               │
│  │  │ Departman:                     │ │               │
│  │  │ [Üretim ▼]                     │ │               │
│  │  │                                │ │               │
│  │  │ [Davet Gönder]                  │ │               │
│  │  └─────────────────────────────────┘ │               │
│  │                                       │               │
│  │  Gönderilen Davetler:                 │               │
│  │  • mehmet@abctekstil.com (Manager)   │               │
│  │  • ayse@abctekstil.com (Supervisor)  │               │
│  │                                       │               │
│  │  [⏭ Skip Later]  [Next →]            │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
│                                                           │
│  API: POST /api/common/users/invite                        │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 10: ONBOARDING WIZARD - STEP 4                     │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  Progress: [████████████████] Step 4/4                   │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Step 4/4: Ödeme Yöntemi            │                │
│  │         (Trial - Ücretsiz)        │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  🎁 14 Gün Ücretsiz Deneme          │               │
│  │                                       │               │
│  │  Deneme süreniz bitince otomatik    │               │
│  │  ücretlendirme başlayacak.          │               │
│  │                                       │               │
│  │  ┌─────────────────────────────────┐ │               │
│  │  │ Kart Sahibi:                  │ │               │
│  │  │ [Ahmet Yılmaz]               │ │               │
│  │  │                                │ │               │
│  │  │ Kart Numarası:                │ │               │
│  │  │ [4242 4242 4242 4242]         │ │               │
│  │  │                                │ │               │
│  │  │ Son Kullanma:                 │ │               │
│  │  │ [12/25]  CVC: [123]           │ │               │
│  │  │                                │ │               │
│  │  │ 💳 Şimdilik ücretlendirilme    │ │               │
│  │  │    Deneme süresi bitince       │ │               │
│  │  │    otomatik başlar.            │ │               │
│  │  └─────────────────────────────────┘ │               │
│  │                                       │               │
│  │  [⏭ Skip]  [Kurulumu Tamamla]      │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
│                                                           │
│  API: POST /api/billing/payment-method                    │
│  (Gelecekte eklenecek)                                    │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 11: COMPLETE ONBOARDING                            │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  ✅ Kurulum Tamamlandı!                                  │
│                                                           │
│  API: POST /api/common/users/me/complete-onboarding      │
│                                                           │
│  Response: {                                              │
│    user: {                                                │
│      hasCompletedOnboarding: true,  // ✅                │
│      onboardingCompletedAt: "2025-01-27T10:30:00Z"         │
│    }                                                      │
│  }                                                       │
│                                                           │
│  [👉 Dashboard'a Yönlendir]                              │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 12: DASHBOARD                                       │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  URL: /dashboard                                          │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ 🎉 Hoş Geldiniz, Ahmet!              │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  ABC Tekstil için FabricOS'a         │               │
│  │  başarıyla kayıt oldunuz.            │               │
│  │                                       │               │
│  │  ✅ Onboarding Tamamlandı            │               │
│  │                                       │               │
│  │  [Dashboard'u Görüntüle]             │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
└──────────────────────────────────────────────────────────┘
```

---

## 💼 SALES-LED SIGNUP FLOW

### **Farklılıklar:**

```
┌──────────────────────────────────────────────────────────┐
│  STEP 1: SALES TEAM CREATES TENANT                       │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  Internal Admin Panel:                                   │
│  POST /api/admin/onboarding/tenant                       │
│                                                           │
│  Request: {                                              │
│    companyName: "XYZ Tekstil",                           │
│    taxId: "9876543210",                                  │
│    companyType: "SPINNER",                              │
│    address: "İstanbul",           // Opsiyonel         │
│    city: "İstanbul",              // Opsiyonel         │
│    phoneNumber: "+905551234567",  // Opsiyonel         │
│    companyEmail: "info@xyz.com", // Opsiyonel         │
│    adminFirstName: "Mehmet",                              │
│    adminLastName: "Kaya",                                │
│    adminContact: "mehmet@xyz.com",                        │
│    adminDepartment: "Yönetim",     // Opsiyonel         │
│    selectedOS: ["YarnOS", "FabricOS"],                 │
│    trialDays: 90                   // 90 gün!          │
│  }                                                       │
│                                                           │
│  ✅ Tenant created                                       │
│  ✅ Admin user created                                    │
│  ✅ Subscriptions created (90 days trial)                │
│  ✅ Welcome email sent                                    │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 2: EMAIL (Kullanıcının Email Kutusu)              │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  ┌──────────────────────────────────────┐               │
│  │ 📧 FabricOS - Hesabınız Hazır       │              │
│  ├──────────────────────────────────────┤               │
│  │                                       │               │
│  │  Merhaba Mehmet,                      │               │
│  │                                       │               │
│  │  XYZ Tekstil için FabricOS hesabınız │               │
│  │  hazır! Satış ekibimiz tarafından    │               │
│  │  oluşturuldu.                        │               │
│  │                                       │               │
│  │  ✅ Aktif OS'leriniz:                │               │
│  │     • FabricOS (Base Platform)      │               │
│  │     • YarnOS (Yarn Production)       │               │
│  │                                       │               │
│  │  🎁 90 Gün Ücretsiz Deneme           │               │
│  │                                       │               │
│  │  [Şifremi Oluştur]                    │               │
│  │  https://app.fabricmanagement.com/    │               │
│  │  setup?token=xyz-789-abc-123          │               │
│  │                                       │               │
│  │  ⚠️ Token doğrulaması yeterli        │               │
│  │     (Verification code gerekmez)      │               │
│  │                                       │               │
│  └──────────────────────────────────────┘               │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 3: PASSWORD SETUP (Token Only)                     │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  URL: /setup?token=xyz-789-abc-123                        │
│                                                           │
│  ┌─────────────────────────────────────┐                │
│  │ Şifrenizi Oluşturun                  │                │
│  ├─────────────────────────────────────┤                │
│  │                                       │               │
│  │  Email:                              │               │
│  │  mehmet@xyz.com ✅                    │               │
│  │  (Sales team tarafından doğrulandı)  │               │
│  │                                       │               │
│  │  Şifre:                              │               │
│  │  [••••••••••]                        │               │
│  │                                       │               │
│  │  Şifre Tekrar:                       │               │
│  │  [••••••••••]                        │               │
│  │                                       │               │
│  │  ⚠️ Verification code gerekmez       │               │
│  │     (Sales-led onboarding için)       │               │
│  │                                       │               │
│  │  [Hesabı Aktifleştir]                │               │
│  │                                       │               │
│  └─────────────────────────────────────┘                │
│                                                           │
│  API: POST /api/auth/setup-password                      │
│  Request: {                                              │
│    token: "xyz-789-abc-123",                              │
│    password: "SecurePass123!"                            │
│    // verificationCode: NOT NEEDED                      │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  STEP 4: ONBOARDING WIZARD (Same as Self-Service)        │
│  ──────────────────────────────────────────────────────  │
│                                                           │
│  ⚠️ Sales-led'de de onboarding wizard açılabilir          │
│  (Eğer company profile eksikse)                           │
│                                                           │
│  [Same wizard steps 1-4]                                 │
└──────────────────────────────────────────────────────────┘
```

---

## 📱 DETAYLI ADIMLAR

### **Registration → Password Setup → Onboarding → Dashboard**

```
┌─────────────────────────────────────────────────────────┐
│                    KAYIT AŞAMASI                        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. Frontend: Signup Form                               │
│     ↓                                                    │
│  2. Backend: POST /api/public/signup                     │
│     • Company oluştur (minimal)                         │
│     • User oluştur (minimal)                            │
│     • Subscriptions oluştur                             │
│     • Registration token oluştur                        │
│     ↓                                                    │
│  3. Email gönder (Welcome email + token)                │
│     ↓                                                    │
│  4. Frontend: Success message                            │
│     "Email'inizi kontrol edin"                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                PASSWORD SETUP AŞAMASI                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. User email'deki linke tıklar                        │
│     ↓                                                    │
│  2. Frontend: Password setup page                       │
│     (/setup?token=...)                                  │
│     ↓                                                    │
│  3. User şifre oluşturur                                │
│     ↓                                                    │
│  4. Backend: POST /api/auth/setup-password              │
│     • Token validate                                    │
│     • Password hash                                     │
│     • AuthUser create                                   │
│     • JWT tokens generate                               │
│     • needsOnboarding: true dön                         │
│     ↓                                                    │
│  5. Frontend: Auto-login + redirect                     │
│     if (needsOnboarding) → /onboarding                  │
│                                                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│              ONBOARDING WIZARD AŞAMASI                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Step 1: Company Profile                                │
│  ──────────────────────                                 │
│  • Address bilgileri                                    │
│  • Phone, email, website                                 │
│  • API: PUT /api/common/companies/me/profile            │
│                                                          │
│  Step 2: Departments                                    │
│  ──────────────────────                                 │
│  • Departmanlar oluştur                                 │
│  • API: POST /api/common/departments                    │
│                                                          │
│  Step 3: Team Members                                    │
│  ──────────────────────                                 │
│  • Ekip üyeleri davet et                                │
│  • API: POST /api/common/users/invite                    │
│                                                          │
│  Step 4: Payment Method                                  │
│  ──────────────────────                                 │
│  • Ödeme yöntemi ekle (trial için)                      │
│  • API: POST /api/billing/payment-method                │
│                                                          │
│  Complete:                                               │
│  ──────────────────────                                 │
│  • API: POST /api/common/users/me/complete-onboarding    │
│  • user.onboardingCompletedAt = now()                   │
│                                                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                   DASHBOARD AŞAMASI                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  • User başarıyla onboard edildi                        │
│  • needsOnboarding: false                               │
│  • Dashboard görüntülenir                               │
│  • Tüm özelliklere erişim                               │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## 🔄 API CALL SEQUENCE

### **Complete Flow Sequence:**

```
Frontend              Backend                   Database
   │                     │                          │
   │── POST /signup ────>│                          │
   │                     │── createCompany() ──────>│
   │                     │── createUser() ─────────>│
   │                     │── createSubscriptions() ─>│
   │                     │── createToken() ────────>│
   │                     │── sendEmail() ───────────>│
   │<── Response ───────│                          │
   │                     │                          │
   │ (Email gönderildi mesajı)                      │
   │                     │                          │
   │── Click email link ─>                          │
   │                     │                          │
   │── POST /setup-password ─>│                      │
   │                     │── validateToken() ──────>│
   │                     │── createAuthUser() ─────>│
   │                     │── generateJWT()          │
   │<── LoginResponse ───│                          │
   │  (needsOnboarding: true)                        │
   │                     │                          │
   │── Navigate /onboarding                           │
   │                     │                          │
   │── PUT /companies/me/profile ─>│                  │
   │                     │── updateCompany() ──────>│
   │                     │── createAddress() ──────>│
   │                     │── createContact() ──────>│
   │<── CompanyDto ──────│                          │
   │                     │                          │
   │── POST /departments ─>│                          │
   │                     │── createDepartment() ───>│
   │<── DepartmentDto ──│                          │
   │                     │                          │
   │── POST /users/invite ─>│                         │
   │                     │── createInvitation() ────>│
   │                     │── sendEmail() ───────────>│
   │<── InvitationResponse                           │
   │                     │                          │
   │── POST /payment-method ─>│                        │
   │                     │── createPaymentMethod() ─>│
   │<── PaymentMethodDto │                          │
   │                     │                          │
   │── POST /complete-onboarding ─>│                    │
   │                     │── completeOnboarding() ──>│
   │                     │   user.onboardingCompletedAt = now()
   │<── UserDto ─────────│                          │
   │                     │                          │
   │── Navigate /dashboard                            │
   │                     │                          │
```

---

## ⚠️ HATA SENARYOLARI

### **1. Email Gönderilemedi:**
```
❌ Email servisi çalışmıyor
   → User manuel olarak token alabilir (admin panel)
   → Veya email tekrar gönderilebilir
```

### **2. Token Expired:**
```
❌ Token 24 saat geçti
   → "Token geçersiz veya süresi dolmuş"
   → Yeni token iste (admin panel veya support)
```

### **3. Password Setup Failed:**
```
❌ Token invalid veya user already has password
   → "Bu link zaten kullanılmış"
   → Login sayfasına yönlendir
```

### **4. Onboarding Incomplete:**
```
⚠️ User wizard'ı tamamlamadı
   → needsOnboarding: true kalır
   → Dashboard'da banner göster:
   "Profilinizi tamamlamak için tıklayın"
   → /onboarding'a yönlendir
```

---

## 📊 KULLANICI DENEYİMİ METRİKLERİ

### **Self-Service Flow:**
- **Toplam Süre:** ~5-10 dakika
- **Adım Sayısı:** 4 ana adım + wizard 4 step
- **Friction Points:** Email doğrulama, şifre oluşturma
- **Completion Rate:** %60-70 (beklenen)

### **Sales-Led Flow:**
- **Toplam Süre:** ~3-5 dakika
- **Adım Sayısı:** 3 ana adım + wizard 4 step
- **Friction Points:** Minimal (token only)
- **Completion Rate:** %85-95 (beklenen)

---

## 🎯 ÖZET

**Kullanıcı Deneyimi Akışı:**

1. ✅ **Registration** → Minimal bilgi ile kayıt
2. ✅ **Email Verification** → Token ile doğrulama
3. ✅ **Password Setup** → Şifre oluştur + auto-login
4. ✅ **Onboarding Wizard** → Eksik bilgileri tamamla (4 step)
5. ✅ **Complete Onboarding** → Kurulumu bitir
6. ✅ **Dashboard** → Tam erişim

**Kritik Noktalar:**
- ⚠️ `needsOnboarding` flag'i frontend'i wizard'a yönlendiriyor
- ⚠️ Complete onboarding endpoint'i henüz yok (eklenmeli)
- ⚠️ Wizard adımları opsiyonel (skip edilebilir)
- ✅ Kullanıcı istediği zaman wizard'ı tamamlayabilir

---

**Son Güncelleme:** 2025-01-27


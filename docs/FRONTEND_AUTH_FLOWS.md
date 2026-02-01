# Frontend – Auth Akışları (Backend Düzeltmeleri Sonrası)

Backend’de yapılan düzeltmelerden sonra frontend’in bilmesi gerekenler ve API kullanımı.

---

## 0. Response yapısı (ApiResponse wrapper)

Tüm auth endpoint’leri başarılı yanıtta **ApiResponse** sarmalayıcısı kullanır:

```json
{ "success": true, "data": { ... }, "message": "...", "timestamp": "..." }
```

- **`data`**: Asıl payload (LoginResponse, contacts, string mesaj vb.).
- Axios kullanıyorsanız: `response.data` = HTTP body (yukarıdaki obje), **`response.data.data`** = payload.

**Login response** döndüren endpoint’ler (`/api/auth/setup-password`, `/api/auth/register/verify`, `/api/auth/login`, `/api/auth/password-reset/verify`, `/api/auth/refresh`):

- LoginResponse **`data`** içinde gelir → **`response.data.data`**.
- `needsOnboarding`, `user`, `onboardingPrefill` **mutlaka `response.data.data`** üzerinden okunmalı.  
  Örn. `response.data.data?.needsOnboarding`, `response.data.data?.user`, `response.data.data?.onboardingPrefill`.  
  `response.data` üzerinden bakarsanız bu alanlar `undefined` olur ve onboarding akışı çalışmaz.

---

## 1. Forgot Password (Şifre Sıfırlama)

### Backend’de ne değişti?

- Public endpoint’lerde **tenant context** artık doğru set ediliyor.
- Kullanıcı login sayfasında email girip "Forgot password? Reset using Email or Phone" dediğinde **masked contacts** artık dolu dönüyor (önceden "No verified emails found" geliyordu).

### API contract değişmedi

Aynı endpoint’ler, aynı request/response. Sadece backend artık doğru veriyi döndürüyor.

### Akış (frontend tarafında)

1. **Masked contacts al**
   - `GET /api/auth/user/{contactValue}/masked-contacts`
   - `contactValue`: Login alanındaki email (örn. `akkaya064@gmail.com`)
   - **Response:** `{ "data": { "contacts": [ { "authUserId": "uuid", "maskedValue": "ak***@gmail.com", "type": "EMAIL", "verified": true } ] } }`
   - Artık `contacts` listesi dolu gelmeli (en az 1 eleman). Boş gelirse kullanıcı gerçekten verified contact’ı olmayan bir hesaptır.

2. **Kullanıcıya seçenek göster**
   - `contacts` listesinden Email/Phone seçeneklerini göster (örn. "Send code to ak\*\*\*@gmail.com").
   - Seçilen contact’ın **`authUserId`** değerini sakla; sonraki iki istekte kullanılacak.

3. **Kod gönder**
   - `POST /api/auth/password-reset/request`
   - Body: `{ "authUserId": "<seçilen authUserId>", "contactType": "EMAIL" }` (veya `"PHONE"`)
   - **Response:** `{ "data": "Password reset verification code has been sent to your email." }`

4. **Kodu + yeni şifre ile sıfırlamayı tamamla**
   - `POST /api/auth/password-reset/verify`
   - Body: `{ "authUserId": "<aynı authUserId>", "code": "123456", "newPassword": "yeniSifre123" }`
   - **Response:** Login response (`data` içinde; axios’ta `response.data.data`). accessToken, refreshToken, user, needsOnboarding, onboardingPrefill – kullanıcı otomatik giriş yapmış sayılır.

### Frontend’te kontrol edilecekler

- "Forgot password" → email girildikten sonra **GET masked-contacts** çağrılıyor mu?
- Gelen **`contacts`** listesi boş değilse, her bir contact için **`authUserId`** ve **`maskedValue`** kullanılarak seçim UI’ı gösteriliyor mu?
- **Request reset** ve **verify** isteklerinde hep **seçilen contact’ın `authUserId`** değeri gönderiliyor mu? (Masked value değil.)

---

## 2. İlk Kayıt – E-posta Doğrulama Zamanı

### Backend’de ne değişti?

- İlk kayıtta (self-service signup) e-posta artık **kayıt anında değil**, kullanıcı **maildeki linke tıklayıp şifre set ettiğinde** doğrulanıyor.
- Akış: Register → mail gider → kullanıcı linke tıklar → `/setup?token=...` → şifre set eder → **POST /api/auth/setup-password** → backend hem şifreyi set eder hem contact’ı verify eder.

### Frontend’te değişiklik gerekmiyor

- Zaten link → setup sayfası → şifre → `POST /api/auth/setup-password` akışı kullanılıyorsa, ekstra bir implementasyon gerekmez.
- API contract aynı: `POST /api/auth/setup-password` body: `{ "token": "...", "password": "..." }`, response: Login response (`data` içinde; axios’ta `response.data.data`, §0).

---

## 3. Onboarding Form Ön Doldurma (Register / Setup-Password Sonrası)

### Backend’de ne döner?

- **Login response** (`POST /api/auth/setup-password`, `POST /api/auth/register/verify`, `POST /api/auth/login`) **`data`** içinde gelir (axios’ta `response.data.data`; bkz. §0). İçeriği:
  - `needsOnboarding`: `true` ise kullanıcı onboarding sihirbazını tamamlamamıştır; form gösterilmelidir.
  - `user`: `firstName`, `lastName`, `displayName`, `hasCompletedOnboarding` vb.
  - `onboardingPrefill` (sadece `needsOnboarding === true` iken dolu): Register/signup sırasında girilen bilgilerle onboarding formunun otomatik doldurulması için:
    - `primaryEmail`: Kullanıcının birincil e-posta adresi (login/şifre kurulumunda kullanılan).
    - `companyName`: Şirket (tenant) adı.
    - `taxId`: Şirket vergi numarası (sadece okunabilir gösterim için; signup’ta zaten girildi).
    - `companyType`: Şirket türü (örn. VERTICAL_MILL) (sadece okunabilir gösterim için).

### Frontend’te yapılacaklar

- LoginResponse’u **`response.data.data`** (axios) üzerinden alın (bkz. §0). `data = response.data.data` diyebilirsiniz.
- **`data.needsOnboarding === true`** ise onboarding formunu göster.
- Form alanlarını otomatik doldurmak için:
  - **Ad / Soyad:** `data.user.firstName`, `data.user.lastName`
  - **E-posta:** `data.onboardingPrefill.primaryEmail` (veya `data.user` içinde primary contact varsa onu kullan)
  - **Şirket adı:** `data.onboardingPrefill.companyName`
  - **Vergi numarası:** `data.onboardingPrefill.taxId` — sadece **read-only** gösterin; onboarding’de tekrar input olarak toplamayın (signup’ta zaten kaydedildi).
  - **Şirket türü:** `data.onboardingPrefill.companyType` — sadece **read-only** gösterin.
- **Şirket güncelleme (PUT company):** Ana şirketin (tenant root) vergi numarası backend’de **değiştirilemez** (kayıt sırasında sabitlenir). Onboarding’de şirket bilgisi güncellenirken vergi numarasını değiştirmeye çalışırsanız `400 DOMAIN_RULE_VIOLATION` alırsınız. Vergi numarası alanını disabled/read-only tutun.
- Böylece kullanıcı register veya setup-password sonrası aynı bilgileri tekrar yazmak zorunda kalmaz.

---

## 4. Özet Tablo

| Akış               | Endpoint                                     | Frontend aksiyonu                                                                                                                    |
| ------------------ | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Masked contacts    | `GET /api/auth/user/{email}/masked-contacts` | Email’i path’e koy; `contacts` listesini göster, `authUserId` sakla.                                                                 |
| Kod gönder         | `POST /api/auth/password-reset/request`      | Body: `authUserId`, `contactType` ("EMAIL" \| "PHONE").                                                                              |
| Kod + şifre verify | `POST /api/auth/password-reset/verify`       | Body: `authUserId`, `code`, `newPassword`.                                                                                           |
| Setup password     | `POST /api/auth/setup-password`              | Body: `token`, `password`. Payload `data` içinde; `needsOnboarding`, `onboardingPrefill` → `response.data.data` (§0).                |
| Register verify    | `POST /api/auth/register/verify`             | Body: `contactValue`, `code`, `password`. Payload `data` içinde; `needsOnboarding`, `onboardingPrefill` → `response.data.data` (§0). |

---

## 5. Hata senaryoları

Backend hata yanıtında **ApiError** kullanır; `code` alanı **root’ta** gelir (iç içe `error` objesi yok). Axios’ta `response.data` = body ise, kod kontrolü **`response.data.code`** ile yapılır.

- **contacts boş:** Kullanıcının verified email/phone’u yok. "No verified emails found for this account." gibi mesaj gösterilebilir (backend artık bunu yanlış tenant yüzünden yapmıyor; gerçekten verified yoksa boş döner).
- **401/403:** Endpoint’ler public; normalde gerekmez. CORS/security config’de `/api/auth/**` public ise sorun olmaz.
- **400 Invalid credentials:** Login’de yanlış şifre; forgot password ile ilgili değil.
- **400 Signup çakışmaları (`response.data.code` ile ayırt edin):**
  - `response.data.code === 'TAX_ID_ALREADY_EXISTS'`: Bu vergi numarası ile zaten kayıt yapılmış. Kullanıcıya "Giriş yapın" veya "Şifre sıfırlama" yönlendirmesi yapın.
  - `response.data.code === 'CONTACT_ALREADY_REGISTERED'`: Bu e-posta/telefon zaten kayıtlı. "Giriş yapın" veya farklı bir iletişim bilgisi kullanın mesajı gösterin.
- **400 DOMAIN_RULE_VIOLATION (şirket güncelleme):** Ana şirketin vergi numarası değiştirilemez. Mesaj: "Primary company tax ID cannot be changed. It was set during registration." Onboarding formunda vergi numarasını read-only tutun.

Bu doküman, backend’deki tenant ve e-posta doğrulama düzeltmelerinden sonra frontend’in aynı API’leri kullanarak doğru çalışması için hazırlanmıştır.

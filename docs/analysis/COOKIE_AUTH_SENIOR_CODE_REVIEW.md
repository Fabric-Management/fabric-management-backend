# Code Review Raporu — Cookie-Based Auth Migration

**Skill:** `agents/skills/senior-code-review/SKILL.md`  
**Kapsam:** Cookie tabanlı auth geçişi (backend auth, frontend api-client, E2E).

---

## Özet

İncelemede **kritik** bir hata tespit edildi: Cookie auth kullanıldığında frontend Bearer header göndermediği için AuthController’daki `extractUserIdFromRequest` / `extractTenantIdFromRequest` sadece header’a baktığından session ve MFA endpoint’leri 401/IllegalArgument verebiliyordu. Ayrıca **DRY** ihlali (getClientIpAddress üç controller’da tekrarlanıyordu) ve frontend’de cookie-auth yanıtının (body’de token yok) doğru işlenmemesi riski vardı. Yapılan düzeltmeler: SecurityContext’ten kullanıcı/tenant okuma, ortak WebRequestUtils, AuthenticatedUserContext’e tenantId eklenmesi, frontend mapToAuthResponse’un cookie-auth ile uyumlu hale getirilmesi.

---

## 1. Ölü ve İşlevsiz Kodlar

- **Orta** **AuthController – extractUserIdFromRequest / extractTenantIdFromRequest:** Cookie auth’da kullanılmadıkları için fiilen “yanlış davranışa yol açan” kodlardı; Bearer’a bağımlıydılar. Kaldırıldı; yerine SecurityContext’ten okuyan `getCurrentUserId()` / `getCurrentTenantId()` eklendi.
- **Düşük** **auth.service refreshToken:** Body’de refreshToken göndermek cookie auth’da kullanılmıyor; parametre opsiyonel yapıldı ve body her zaman `{}` gönderilecek şekilde güncellendi (sessizce yutulan eski davranış kaldırıldı).

---

## 2. Kod Kalitesi ve Temiz Kod

- **Orta** **DRY – getClientIpAddress:** AuthController, PasswordController, RegistrationController’da aynı mantık üç kez vardı. `WebRequestUtils.getClientIpAddress(HttpServletRequest)` ortak metoda taşındı; üç controller bu metodu kullanacak şekilde güncellendi.
- **Orta** **Single Responsibility / Cookie vs Header:** Kullanıcı kimliği artık tek kaynaktan (SecurityContext) alınıyor; JwtAuthenticationFilter cookie veya Bearer ile context’i dolduruyor, controller’lar header’a bakmıyor. Böylece cookie-auth ile tutarlı tek sorumluluk sağlandı.

---

## 3. Tip Güvenliği ve Hata Yönetimi

- **Orta** **Frontend mapToAuthResponse:** Backend token’ları body’de dönmediği için `!payload.accessToken` kontrolü “Missing access token” fırlatıyordu. Cookie-auth’da token’lar Set-Cookie’de olduğundan, body’de user varsa veya token alanları boş olsa bile geçerli yanıt kabul edilecek şekilde güncellendi; sadece hem user hem token’lar yoksa hata fırlatılıyor.
- **Düşük** **api-client refresh catch:** Refresh hatası tamamen sessiz yutuluyordu. Geliştirme ortamında `console.debug` ile log eklendi; production’da gürültü yapmıyor.

---

## 4. Performans ve Güvenlik

- **Kritik** **Cookie auth ile session/MFA:** Kullanıcı/tenant artık SecurityContext’ten okunuyor; Bearer zorunluluğu kaldırıldı. Böylece cookie-only istemciler session listesi, MFA setup/confirm/disable/status endpoint’lerini kullanabiliyor (güvenlik ve işlevsellik düzeltmesi).
- **Mevcut iyi:** CORS `allowCredentials(true)` ile wildcard origin kullanılmıyor; dev’de localhost/127.0.0.1 pattern’leri, prod’da `CORS_ALLOWED_ORIGINS` kullanılıyor. Cookie SameSite=Strict, HttpOnly, Secure (configurable) zaten uygulanıyor.

---

## Düzeltilmiş / Refactor Edilmiş Kod Özeti

| Dosya | Değişiklik |
|-------|------------|
| **AuthenticatedUserContext.java** | `tenantId` (UUID, nullable) eklendi; JWT claim `tenant_id` ile besleniyor. |
| **JwtAuthenticationFilter.java** | `getTenantIdFromToken` ile tenantId alınıp `AuthenticatedUserContext`’e veriliyor. |
| **AuthController.java** | `extractUserIdFromRequest` / `extractTenantIdFromRequest` kaldırıldı. `getCurrentUserId()` ve `getCurrentTenantId()` SecurityContextHolder + AuthenticatedUserContext kullanıyor. Session ve MFA endpoint’leri bu yardımcıları kullanıyor. `getClientIpAddress` → `WebRequestUtils.getClientIpAddress`. |
| **WebRequestUtils.java** | Yeni: `getClientIpAddress(HttpServletRequest)` (X-Forwarded-For + remote addr). |
| **PasswordController.java** | `getClientIpAddress` kaldırıldı; `WebRequestUtils.getClientIpAddress` kullanılıyor. |
| **RegistrationController.java** | Aynı şekilde `WebRequestUtils.getClientIpAddress` ve private `getClientIpAddress` kaldırıldı. |
| **auth.service.ts** | `mapToAuthResponse`: cookie-auth’da body’de token olmasa da user varsa başarı kabul ediliyor; token alanları `?? ""` ile güvenli. `refreshToken(_data?)`: body her zaman `{}`, parametre opsiyonel. |
| **api-client.ts** | 401 refresh catch’te development’ta `console.debug` ile log. |

---

## Sonuç

Cookie-based auth migration, senior code review kriterlerine göre güncellendi: **kritik** kullanıcı/tenant çözümlemesi SecurityContext’e taşındı, **DRY** getClientIpAddress tek yerde toplandı, frontend cookie-auth yanıtları ve refresh hata log’u iyileştirildi. E2E (login, logout, silent refresh, cookie temizliği) mevcut testlerle uyumlu bırakıldı.

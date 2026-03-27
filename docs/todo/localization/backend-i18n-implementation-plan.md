# Backend İş Planı (Implementation Plan): Multi-Tenant i18n ve Lokalizasyon

Bu doküman, "Agnostik Backend - Çeviren Frontend" mimarisini ve "Hiyerarşik Zaman/Dil (Cascading Preferences)" kurgusunu Spring Boot backend tarafında kusursuz bir şekilde hayata geçirmek için gereken teknik adımları içerir. Bu adımlar, en verimli pratikler (Best Practices) gözetilerek sıralanmıştır.

## Faz 1: Veritabanı (Database) ve Model Güncellemeleri
Kullanıcılara ve firmalara dillerini/saat dilimlerini seçme özgürlüğü sağlamak.

- [ ] **Flyway/Liquibase Migration Yazılması:**
  - `tenant` tablosuna `default_locale` (VARCHAR, örn: 'en-US') ve `default_timezone` (VARCHAR, örn: 'UTC') kolonları eklenecek.
  - `user` (veya `platform_user`) tablosuna `preferred_locale` (VARCHAR, nullable) ve `preferred_timezone` (VARCHAR, nullable) kolonları eklenecek.
- [ ] **Entity Güncellemeleri:**
  - `Tenant` ve `User` JPA Entity sınıflarına ilgili field'lar (String) ve `@Column` notasyonları eklenecek.
- [ ] **DTO ve API Güncellemeleri:**
  - `UserResponse` ve `TenantResponse` (Giriş yapıldığında dönen '/api/v1/auth/me' veya '/users/me' endpoint'i) nesnelerine profil ayarları eklenecek (Frontend'in Context alabilmesi için çok kritik).
  - Profil güncelleme API'si (Örn: `UpdateProfileRequest`) kullanıcının `locale` ve `timezone` ayarlarını değiştirebilmesine imkan verecek.

## Faz 2: Standartlaştırılmış "Error Code" (Agnostik API) Mimarisi 
Backend'in çevrilmiş metinler fırlatmasını bırakıp, hata kodları üretecek şekilde güncellenmesi.

- [ ] **`ApiErrorResponse` Sınıfının Revize Edilmesi:**
  Hata dönüş DTO'suna sadece kod ve parametrelerin ineceği bir yapı tasarlanacak.
  ```java
  public class ApiErrorResponse {
      private String errorCode;          // Örn: "ERROR_PASSWORD_SHORT"
      private Object[] args;             // Örn: [8] (Varsa dinamik değerler)
      private OffsetDateTime timestamp;
  }
  ```
- [ ] **`BusinessException` (veya `DomainException`) Sınıfının Oluşturulması:**
  Artık kodun her yerinden exception atarken düz String değil, kod atılacak:
  ```java
  // YANLIŞ: throw new IllegalArgumentException("Şifre en az 8 haneli olmalı");
  // DOĞRU: 
  throw new BusinessException("ERROR_PASSWORD_SHORT", 8);
  ```
- [ ] **`GlobalExceptionHandler` (@ControllerAdvice) Güncellemesi:**
  Spring'in hata yakalayıcı katmanı, atılan bu `BusinessException`'ları yakalayıp HTTP 400 ile yeni `ApiErrorResponse` JSON nesnesine çevirip Frontend'e dönmelidir. Böylece Frontend `error.response.data.errorCode` okuyup anında Sonner (Toast) üzerinden TR/EN basabilir.

## Faz 3: `Accept-Language` Yönetimi ve Merkezi (Context) Yakalama
Sistemin (Spring'in), isteği atan kullanıcının dil beklentisine anında erişebilmesi.

- [ ] **`LocaleResolver` ve Kurulumu:**
  Spring Boot'un yerleşik (built-in) `AcceptHeaderLocaleResolver` sınıfı yapılandırılmalı veya özelleştirilmelidir (Custom LocaleResolver).
- [ ] **`UserContext` / `ThreadLocal` İçine Enjeksiyon (Zorunlu Değil, Ancak Çok Pratik):**
  İstek API'ye ulaştığında bir **Filter** (örn: `JwtAuthenticationFilter` veya yeni `LocalizationFilter`) `Accept-Language` header'ını okumalı ve bunu o thread için aktif olan (ThreadLocal tabanlı) `TenantContext` varyasyonuna (`LocalizationContext` vb.) yerleştirmelidir.

## Faz 4: Arka Plan Süreçleri ve Email Formatlama `MessageSource` (İstisnalar)
Frontend'in bulunmadığı senaryolar için sunucu tabanlı çok dilli render işlemlerinin yapılması.

- [ ] **`messages_en.properties` ve `messages_tr.properties` Dosyalarının Eklenmesi:**
  `src/main/resources/i18n` klasörüne sadece sistemsel çeviriler (Örn: Email şablon değişkenleri, Fatura PDF başlıkları) yerleştirilecek.
- [ ] **`ResourceBundleMessageSource` Bean'inin Yapılandırılması (Konfigürasyon):**
  UTF-8 destekleyecek, `classpath:i18n/messages` yolunu baz alacak MessageSource bean'i Fallback (Varsayılan EN) kuralıyla konfigüre edilecek.
- [ ] **Custom `LocalizationService` (Yardımcı Sınıf) Oluşturulması:**
  Her yerde Spring'in karmaşık MessageSource metodlarını kullanmamak için sade bir Utils servisi yaratın:
  ```java
  public String getMessage(String code, Object[] args, Locale locale) {
      return messageSource.getMessage(code, args, "Undefined message", locale);
  }
  ```
- [ ] **Hiyerarşik Tercihlerin Uygulanması (Scheduler ve Event'ler için):**
  Kafka mesajlarından (Event Listener) veya Scheduler Job'lardan (`ApprovalExpiryJob`) bir bildirim mail'i tetiklendiğinde; E-postayı atacak Servis, yukarıda bahsedilen 3 kademeli (1. User -> 2. Tenant -> 3. Fallback) kuralına göre kullanıcının `Locale`'ini veritabanından seçecek ve `LocalizationService`'den o e-postanın taslağını o dilde çekip gönderecek. Zaman verileri (`OffsetDateTime`) kullanıcıya mailde gösterilirken de, kullanıcının profilindeki `timezone`'a göre  (Örn: `java.time.ZoneId.of(user.getPreferredTimezone())`) formatlanıp asıl mesaja dönüştürülecek.

## Kütüphane Notları:
Bu tasarım için 3. parti veya şişirilmiş herhangi ekstra bir Java kütüphanesine ihtiyacınız yoktur. Spring WebMVC'deki yerleşik `LocaleResolver`, `MessageSource` ve standart Java `java.time.ZoneId` sınıfları dünyanın en performanslı uygulamalarında bile bu modeli işletmek için fazlasıyla yeterlidir. Dış bağımlılık kullanmamak uzun vadeli stabilite sağlar.

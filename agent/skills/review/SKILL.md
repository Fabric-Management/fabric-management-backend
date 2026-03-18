---
name: backend-code-review
description: Senior Staff Engineer backend code review for Java/Spring Boot, Node.js, Python and similar server-side codebases. Reviews dead code, clean code/architecture (DRY, KISS, SOLID, Hexagonal), type safety/error handling, performance (N+1 queries, connection pools, caching), security (SQL injection, CORS, auth), API design, concurrency, transaction management, and observability. Use when the user asks for backend code review, backend kod incelemesi, servis katmanı review, API review, or when reviewing server-side pull requests or shared backend code. Also triggers on: "bu service'i incele", "repository review", "controller review", "bu endpoint'i kontrol et".
---

# Backend Code Review

Paylaşılan backend kodunu endüstri standartlarına göre inceler, önem derecesine göre kategorize edilmiş detaylı bir rapor üretir. Her bulgu için **neden** sorunlu olduğu açıklanır ve raporun sonunda **düzeltilmiş kod** önerilir.

---

## Ön Adım: Bağlam Tespiti

İncelemeye başlamadan önce şunları belirle:

- **Dil ve framework**: Java/Spring Boot, Node.js/Express, Python/FastAPI vb.
- **Mimari katman**: Controller, Service, Repository, Domain, Config, DTO, Event Listener
- **Modül bağlamı**: Hangi bounded context / modüle ait? Bağımlılıkları neler?

Bu bilgi, inceleme kriterlerinin ağırlıklandırılmasını ve dile özgü önerilerin somutlaştırılmasını sağlar.

---

## İnceleme Kriterleri (6 Ana Başlık)

### 1. Ölü ve İşlevsiz Kodlar (Dead Code & Cleanup)

Kontrol et:

- Kullanılmayan değişkenler, metotlar, sınıflar veya ulaşılamayan kod blokları
- Gereksiz import'lar (IDE'nin gri gösterdiği satırlar)
- Yorum satırına alınmış eski kod (zombie code) — versiyon kontrolünde zaten mevcut, silinmeli
- Kullanılmayan Spring bean tanımları, boş `@Configuration` sınıfları
- Hiçbir yerde çağrılmayan private metotlar
- Deprecated API kullanımı (ör: `Date` yerine `java.time`, `WebSecurityConfigurerAdapter` yerine `SecurityFilterChain`)

Her bulguda: Hangi satır/blok, neden ölü kod, nasıl temizlenmeli.

---

### 2. Kod Kalitesi ve Mimari (Clean Code & Architecture)

Kontrol et:

- **DRY**: Tekrarlanan iş mantığı, query pattern'ları, validation logic'i — ortak abstraction'a taşınmalı mı?
- **KISS**: Gereksiz abstraction katmanı, overengineering, kullanılmayan generic'ler
- **SOLID**:
  - Single Responsibility: Service class birden fazla aggregate'e mi dokunuyor? Birden fazla use case'i mi karşılıyor?
  - Open/Closed: Yeni durum eklemek mevcut kodu değiştirmeyi gerektiriyor mu? (if/else zincirleri → Strategy/Policy pattern)
  - Dependency Inversion: Somut sınıfa mı yoksa interface'e mi bağımlı?
- **İsimlendirme**: Method isimleri ne yaptığını anlatıyor mu? (`process()`, `handle()`, `doStuff()` gibi belirsiz isimler)
- **Metot boyutu**: 30+ satır metotlar bölünmeli; iç içe if/try blokları düzleştirilmeli (early return, guard clause)
- **Katman ihlali**: Controller'da iş mantığı var mı? Repository'de business rule var mı? Domain nesnesi framework bağımlılığı taşıyor mu?
- **Code smell**: God class, Feature Envy, Primitive Obsession, Long Parameter List

Her bulguda: Hangi prensip ihlal edilmiş, neden sorun, nasıl refactor edilmeli.

---

### 3. Tip Güvenliği ve Hata Yönetimi (Type Safety & Error Handling)

Kontrol et:

- **Tip tanımlamaları**: Raw type kullanımı (`List` yerine `List<Fiber>`), gereksiz casting, `Object` dönüş tipi
- **Null safety**: `Optional` doğru kullanılıyor mu? (`Optional.get()` guard'sız çağrı, Optional field, Optional parametre — bunlar anti-pattern). `@NonNull`/`@Nullable` annotation'ları tutarlı mı?
- **Exception stratejisi**:
  - Catch-all (`catch (Exception e)`) yerine spesifik exception
  - Sessizce yutulan exception (boş catch bloğu, sadece log atan catch)
  - Business exception vs technical exception ayrımı yapılmış mı?
  - `@ControllerAdvice` / global exception handler tutarlı mı?
- **Validation**: Input validation nerede yapılıyor? (`@Valid`, custom validator, veya service katmanında manuel). DTO'da `@NotNull`, `@Size`, `@Pattern` gibi constraint'ler var mı?
- **Edge case'ler**: Boş liste, sıfır miktar, negatif değer, tarih sınırları ele alınmış mı?

Her bulguda: Hangi senaryo eksik, olası hata, önerilen düzeltme.

---

### 4. Performans ve Güvenlik (Performance & Security)

Kontrol et:

**Performans:**

- **N+1 sorgu**: Döngü içinde lazy load, `@OneToMany` default fetch, ilişkiler için tek tek sorgu. Çözüm: `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, DTO projection
- **Sorgu optimizasyonu**: `SELECT *` yerine gerekli alanlar, pagination eksikliği (`findAll()` tüm tabloyu mu çekiyor?), index kullanımı
- **Connection pool**: Uzun süren işlem connection'ı blokluyor mu? Transaction scope gereğinden geniş mi?
- **Caching**: Sık okunan, nadir değişen veri cache'lenmeli mi? Cache invalidation stratejisi var mı?
- **Zaman karmaşıklığı**: O(n²) döngü, `List.contains()` yerine `Set`, büyük koleksiyonlarda stream vs. batch işlem

**Güvenlik:**

- **Injection**: SQL/JPQL injection (string concat ile query), Command injection, LDAP injection
- **CORS**: `Access-Control-Allow-Origin: *` production'da tehlike. `credentials: true` + wildcard = güvenlik açığı
- **Auth/Authz**: Endpoint'lerde `@PreAuthorize` veya rol kontrolü var mı? IDOR (Insecure Direct Object Reference) riski — kullanıcı başkasının verisine erişebilir mi?
- **Hassas veri**: Password, token, PII loglanıyor mu? Response'da gereksiz veri dönülüyor mu? (ör: user entity'sinin password hash'i DTO'ya sızması)
- **Dependency güvenliği**: Bilinen CVE'si olan kütüphane sürümü kullanılıyor mu?

Her bulguda: Risk, neden sorun, iyileştirme önerisi.

---

### 5. Transaction Yönetimi ve Veri Tutarlılığı

Kontrol et:

- **Transaction sınırları**: `@Transactional` doğru seviyede mi? (Controller'da olmamalı, genelde Service katmanında). Read-only işlemler `@Transactional(readOnly = true)` mi?
- **Propagation**: İç içe service çağrılarında propagation tipi bilinçli mi? `REQUIRES_NEW` gerçekten gerekli mi?
- **Event-driven tutarlılık**: Domain event publish edildikten sonra transaction rollback olursa event geri alınabiliyor mu? (`@TransactionalEventListener(phase = AFTER_COMMIT)` kullanılıyor mu?)
- **Concurrency**: Race condition riski var mı? Optimistic locking (`@Version`) veya pessimistic locking gerekli mi? Stale data okuma riski
- **Idempotency**: Tekrarlanan istek aynı sonucu verir mi? (Özellikle event handler'lar ve webhook'lar için)

Her bulguda: Tutarsızlık senaryosu, veri kaybı riski, önerilen strateji.

---

### 6. API Tasarımı ve Gözlemlenebilirlik (API Design & Observability)

Kontrol et:

**API Tasarımı:**

- RESTful convention'lara uygun mu? (HTTP method, status code, URI naming)
- DTO vs Entity: Doğrudan entity dönülüyor mu? (Circular reference, lazy loading exception, gereksiz veri sızıntısı)
- Pagination, sorting, filtering desteği var mı? (Özellikle liste endpoint'leri)
- API versiyonlama stratejisi düşünülmüş mü?
- Response envelope tutarlı mı? (success/error formatı)

**Gözlemlenebilirlik:**

- Anlamlı log mesajları var mı? Log seviyesi doğru mu? (DEBUG vs INFO vs WARN vs ERROR)
- Structured logging kullanılıyor mu? (key-value pair, MDC context)
- Metrik / trace / health check endpoint'leri mevcut mu?
- Hata durumunda yeterli context loglanıyor mu? (correlation ID, request ID)

Her bulguda: Eksik olan ne, neden önemli, nasıl eklenmeli.

---

## Önem Derecesi (Severity)

| Etiket                 | Anlamı                                                                  | Örnek                                                           |
| ---------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------- |
| **🔴 Kritik**          | Merge öncesi mutlaka düzeltilmeli; bug, veri kaybı veya güvenlik riski. | NPE riski, SQL injection, transaction tutarsızlığı, auth bypass |
| **🟡 Orta**            | Kalite ve sürdürülebilirlik için düzeltilmesi önerilir.                 | DRY ihlali, N+1 query, eksik validation, raw type kullanımı     |
| **🟢 Düşük / Tavsiye** | İyileştirme önerisi; zorunlu değil ama kodu daha iyi yapar.             | İsimlendirme, log seviyesi, küçük refactor fırsatı              |

---

## Rapor Formatı

```markdown
# Backend Code Review Raporu

## Özet

[2–3 cümle: Genel değerlendirme, kritik bulgu sayısı, öne çıkan alanlar.]

## 1. Ölü ve İşlevsiz Kodlar

- **[🔴/🟡/🟢]** [Bulgu]: [Açıklama]. [Neden]. [Öneri.]

## 2. Kod Kalitesi ve Mimari

- **[🔴/🟡/🟢]** [Bulgu]: [Açıklama]. [Neden]. [Öneri.]

## 3. Tip Güvenliği ve Hata Yönetimi

- **[🔴/🟡/🟢]** [Bulgu]: [Açıklama]. [Neden]. [Öneri.]

## 4. Performans ve Güvenlik

- **[🔴/🟡/🟢]** [Bulgu]: [Açıklama]. [Neden]. [Öneri.]

## 5. Transaction Yönetimi ve Veri Tutarlılığı

- **[🔴/🟡/🟢]** [Bulgu]: [Açıklama]. [Neden]. [Öneri.]

## 6. API Tasarımı ve Gözlemlenebilirlik

- **[🔴/🟡/🟢]** [Bulgu]: [Açıklama]. [Neden]. [Öneri.]

## Düzeltilmiş / Refactor Edilmiş Kod

[Önerilen temiz kod versiyonu — dosya/dosyalar veya anlamlı snippet'ler halinde.]
```

---

## Kurallar

1. Sadece "yanlış" demek yeterli değil: Her madde için **neden** ve **nasıl düzeltileceği** yazılmalı.
2. Sonunda mutlaka refactor edilmiş kod veya net patch/snippet öner.
3. Öncelik sırası: Önce 🔴 Kritik, sonra 🟡 Orta, en sonda 🟢 Düşük/Tavsiye. Aynı seviyede birden fazla bulgu varsa etkiye göre sırala.
4. Bağlam bilincine sahip ol: Spring Boot projesi mi, Node.js mi, Python mu? Framework'e özgü best practice'leri uygula.
5. İnceleme pozitif yönleri de not etmeli — iyi yapılmış kısımları 1-2 cümle ile belirt (motivasyon ve bağlam için).

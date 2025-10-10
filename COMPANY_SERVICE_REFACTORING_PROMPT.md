# 🎯 Company-Service Refactoring Prompt

## İlk Talimat (ZORUNLU)

**Önce şu dokümantasyonları DİKKATLE oku:**

1. 🔴 **ZORUNLU:** `docs/AI_ASSISTANT_LEARNINGS.md` - Kodlama prensipleri ve kurallar
2. 🔴 **ZORUNLU:** `docs/SECURITY.md` - Security standartları
3. 🔴 **ZORUNLU:** `docs/development/PRINCIPLES.md` - Kodlama prensipleri
4. 🔴 **ZORUNLU:** `docs/development/CODE_STRUCTURE_GUIDE.md` - Klasör yapısı

**Bu dosyaları okumadan hiçbir kod yazma!**

---

## 🎯 Görev: Company-Service Refactoring

User-Service'de uyguladığımız **10 Golden Rules** ve **Clean Architecture** prensiplerini Company-Service'e de uygula.

---

## 📋 Yapılacaklar (Sırayla)

### 1️⃣ **Analiz Aşaması**

#### A. Mevcut Durumu İncele

```bash
# Company entity'yi oku
services/company-service/src/main/java/com/fabricmanagement/company/domain/aggregate/Company.java

# Service sınıflarını oku
services/company-service/src/main/java/com/fabricmanagement/company/application/service/*.java

# Controller'ları oku
services/company-service/src/main/java/com/fabricmanagement/company/api/*.java
```

#### B. Sorunları Tespit Et

- [ ] Company entity kaç satır? Şişmiş mi?
- [ ] Entity'de business method var mı?
- [ ] Service'de mapping logic var mı?
- [ ] Controller'da logic var mı?
- [ ] DTO'lar organize mi (request/response)?
- [ ] Gereksiz sınıf var mı? (validator, helper, domain-service)
- [ ] Comment noise var mı?

---

### 2️⃣ **Refactoring Aşaması**

#### A. DTO Organizasyonu

```
Hedef:
api/dto/
├── request/
│   ├── CreateCompanyRequest.java
│   ├── UpdateCompanyRequest.java
│   └── ...
└── response/
    ├── CompanyResponse.java
    └── ...
```

**Aksiyon:**

- [ ] request/ ve response/ klasörleri oluştur
- [ ] Tüm DTO'ları taşı
- [ ] Import'ları güncelle
- [ ] Eski DTO'ları sil

#### B. Entity Temizliği (Anemic Domain)

```java
// Hedef: Company.java sadece data holder
@Entity
@Getter
@Setter
@SuperBuilder
public class Company extends BaseEntity {
    private UUID tenantId;
    private String name;
    // ... fields only, NO METHODS!
}
```

**Aksiyon:**

- [ ] Entity'deki tüm business methodları kaldır
- [ ] Computed properties → Mapper'a taşı
- [ ] Factory methodları sil
- [ ] Domain event logic'i kaldır

#### C. Mapper Oluştur

```
Hedef:
application/mapper/
├── CompanyMapper.java       # DTO ↔ Entity
├── CompanyEventMapper.java  # Entity → Event
└── CompanyUserMapper.java   # İlişki mapping (varsa)
```

**Aksiyon:**

- [ ] CompanyMapper.java oluştur (fromCreateRequest, toResponse, etc.)
- [ ] CompanyEventMapper.java oluştur (toCreatedEvent, etc.)
- [ ] Service'deki mapping logic'i Mapper'a taşı
- [ ] Event building logic'i EventMapper'a taşı

#### D. Service Temizliği

```java
// Hedef: CompanyService sadece business logic
@Service
public class CompanyService {

    @Transactional
    public UUID createCompany(CreateCompanyRequest request, UUID tenantId, String createdBy) {
        // NO MAPPING! Delegate to mapper
        Company company = companyMapper.fromCreateRequest(request, tenantId, createdBy);
        company = companyRepository.save(company);

        // NO EVENT BUILDING! Delegate to eventMapper
        eventPublisher.publishCompanyCreated(
            eventMapper.toCreatedEvent(company)
        );

        return company.getId();
    }
}
```

**Aksiyon:**

- [ ] Tüm mapping logic'i kaldır → Mapper'a taşı
- [ ] Event building'i kaldır → EventMapper'a taşı
- [ ] Gereksiz comment'leri sil
- [ ] Method isimlerini kontrol et (misleading var mı?)

#### E. Controller Temizliği

```java
// Hedef: Controller sadece HTTP handling
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UUID>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {

        UUID companyId = companyService.createCompany(request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(companyId, "Company created successfully"));
    }
}
```

**Aksiyon:**

- [ ] Gereksiz comment'leri sil
- [ ] Tekrar eden kod'u extract et (örn: sort parsing)
- [ ] Log statement'ları basitleştir

#### F. Gereksiz Sınıfları Kaldır

```
Kontrol et ve sil:
- [ ] CompanyValidator (varsa) → Spring @Valid yeterli
- [ ] CompanyHelper (varsa) → Private method yap
- [ ] CompanyEnricher (varsa) → Mapper'a merge et
- [ ] CompanySearchService (varsa) → CompanyService'e entegre et
```

#### G. Infrastructure Düzeltmeleri

```
Kontrol et:
- [ ] infrastructure/persistence/ mi yoksa infrastructure/repository/ mi?
- [ ] Security concern varsa → infrastructure/security/
- [ ] Cache concern varsa → infrastructure/cache/
```

---

### 3️⃣ **Doğrulama Aşaması**

#### A. Kod Kalitesi

- [ ] Entity 200 satırın altında mı?
- [ ] Service'de mapping logic var mı? (Olmamalı!)
- [ ] Controller'da business logic var mı? (Olmamalı!)
- [ ] Comment noise temizlendi mi?

#### B. Prensip Kontrolü

- [ ] **SRP:** Her sınıf tek sorumluluk mu?
- [ ] **DRY:** Kod tekrarı var mı?
- [ ] **KISS:** Basit mi, over-engineering yok mu?
- [ ] **YAGNI:** Gereksiz abstraction var mı?
- [ ] **Anemic Domain:** Entity sadece data mı?

#### C. Lint & Test

- [ ] `read_lints` ile hata kontrolü yap
- [ ] Import'lar temiz mi?
- [ ] Kullanılmayan import var mı?

---

## 🏆 Başarı Kriterleri

### Hedef Metrikler (User-Service Benzeri):

| Metrik               | Hedef                            |
| -------------------- | -------------------------------- |
| **Company Entity**   | <150 satır (pure data holder)    |
| **CompanyService**   | <200 satır (business logic only) |
| **Mapper Sayısı**    | 2-3 mapper (SRP)                 |
| **Over-engineering** | 0 gereksiz sınıf                 |
| **Comment Noise**    | Minimal (sadece WHY)             |
| **LOC Azalması**     | -30% to -50%                     |

---

## ⚠️ YAPMAMANLAR (Anti-Patterns)

### ❌ YAPMAA:

1. ❌ Yeni validator/helper/builder sınıfı oluşturma
2. ❌ Service'de mapping logic bırakma
3. ❌ Entity'de business method bırakma
4. ❌ Gereksiz comment ekleme
5. ❌ Over-engineering yapma
6. ❌ Spring/Lombok'un yaptığını tekrar yazma

### ✅ YAP:

1. ✅ Önce dokümantasyonu oku (AI_LEARNINGS, PRINCIPLES, CODE_STRUCTURE)
2. ✅ Mevcut kodu analiz et
3. ✅ Mapping → Mapper'a taşı
4. ✅ Entity → Pure data holder yap
5. ✅ Service → Sadece business logic bırak
6. ✅ Comment'leri temizle
7. ✅ Etkilenen tüm kodları güncelle
8. ✅ Test et ve doğrula

---

## 🎯 Beklenen Sonuç

### Kod Kalitesi:

- ✅ Company entity: Pure data holder (~100-150 satır)
- ✅ CompanyService: Business logic only (~150-200 satır)
- ✅ 2-3 Mapper: SRP uygulanmış
- ✅ Clean controllers: Minimal logic
- ✅ Zero over-engineering
- ✅ Self-documenting code

### Yapı:

```
company-service/
├── api/
│   ├── CompanyController.java
│   └── dto/
│       ├── request/
│       └── response/
│
├── application/
│   ├── mapper/
│   │   ├── CompanyMapper.java
│   │   └── CompanyEventMapper.java
│   └── service/
│       └── CompanyService.java
│
├── domain/
│   ├── aggregate/Company.java (pure data)
│   ├── event/
│   └── valueobject/
│
└── infrastructure/
    ├── repository/
    ├── client/
    ├── messaging/
    └── config/
```

---

## 📝 Rapor Formatı

İşlem tamamlandığında şu formatta rapor ver:

```markdown
## 🎉 Company-Service Refactoring TAMAMLANDI!

### 📊 Sonuçlar

| Dosya               | ÖNCE | SONRA | İyileştirme |
| ------------------- | ---- | ----- | ----------- |
| Company.java        | XXX  | XXX   | -XX%        |
| CompanyService.java | XXX  | XXX   | -XX%        |

### ✅ Yapılanlar

1. DTO organizasyonu
2. Entity temizliği
3. Mapper oluşturma
4. ...

### 🏆 Uygulanan Prensipler

- SRP, DRY, KISS, YAGNI
- Anemic Domain
- Mapper Separation
```

---

**Hazırlayan:** User + AI Team  
**Tarih:** 2025-10-10  
**Hedef:** Company-Service Production Ready  
**Başarı:** User-Service benzeri kalite

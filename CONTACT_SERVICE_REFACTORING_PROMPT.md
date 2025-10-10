# 🎯 Contact-Service Refactoring Prompt

## İlk Talimat (ZORUNLU)

**Önce şu dokümantasyonları DİKKATLE oku:**

1. 🔴 **ZORUNLU:** `docs/AI_ASSISTANT_LEARNINGS.md` - Kodlama prensipleri ve kurallar
2. 🔴 **ZORUNLU:** `docs/SECURITY.md` - Security standartları
3. 🔴 **ZORUNLU:** `docs/development/PRINCIPLES.md` - Kodlama prensipleri
4. 🔴 **ZORUNLU:** `docs/development/CODE_STRUCTURE_GUIDE.md` - Klasör yapısı
5. 🟡 **REFERANS:** `COMPANY_SERVICE_REFACTORING_COMPLETE.md` - Başarılı refactoring örneği
6. 🟡 **REFERANS:** `POLICY_USAGE_ANALYSIS_AND_RECOMMENDATIONS.md` - Policy kullanımı

**Bu dosyaları okumadan hiçbir kod yazma!**

---

## 🎯 Görev: Contact-Service Refactoring

User-Service ve Company-Service'de uyguladığımız **10 Golden Rules** ve **Clean Architecture** prensiplerini Contact-Service'e de uygula.

---

## ⚠️ ÖNEMLİ NOT: POLICY KULLANMA!

```
╔═══════════════════════════════════════════════════════════════╗
║                                                               ║
║  ❌ CONTACT-SERVICE'DE POLICY KULLANMA!                      ║
║                                                               ║
║  Neden:                                                       ║
║  - Basit domain (email, phone, address)                      ║
║  - Owner-based authorization yeterli                         ║
║  - Cross-company access business requirement değil           ║
║  - KISS & YAGNI prensipleri (gereksiz complexity ekleme)     ║
║                                                               ║
║  ✅ KULLAN: Simple owner check                               ║
║  ❌ KULLANMA: PolicyEngine, PolicyContext, CompanyType       ║
║                                                               ║
╚═══════════════════════════════════════════════════════════════╝
```

---

## 📋 Yapılacaklar (Sırayla)

### 1️⃣ **Analiz Aşaması**

#### A. Mevcut Durumu İncele

```bash
# Contact entity'yi oku
services/contact-service/src/main/java/com/fabricmanagement/contact/domain/aggregate/Contact.java

# Service sınıflarını oku
services/contact-service/src/main/java/com/fabricmanagement/contact/application/service/*.java

# Controller'ları oku
services/contact-service/src/main/java/com/fabricmanagement/contact/api/*.java
```

#### B. Sorunları Tespit Et

- [ ] Contact entity kaç satır? Şişmiş mi?
- [ ] Entity'de business method var mı? (verify, makePrimary - bunlar KALACAK!)
- [ ] Service'de mapping logic var mı?
- [ ] Controller'da logic var mı?
- [ ] DTO'lar organize mi (request/response)?
- [ ] Gereksiz sınıf var mı?
- [ ] Comment noise var mı?

---

### 2️⃣ **Refactoring Aşaması**

#### A. DTO Organizasyonu

```
Hedef:
api/dto/
├── request/
│   ├── CreateContactRequest.java
│   ├── UpdateContactRequest.java
│   ├── VerifyContactRequest.java
│   └── ...
└── response/
    ├── ContactResponse.java
    └── ...
```

**Aksiyon:**

- [ ] request/ ve response/ klasörleri oluştur
- [ ] Tüm DTO'ları taşı
- [ ] Import'ları güncelle
- [ ] Eski DTO'ları sil

#### B. Entity Kararı (⚠️ DİKKATLİ!)

**Contact için İKİ SEÇENEK var:**

**Seçenek 1: Rich Domain Model (ÖNERİLEN)** ✅

```java
// ✅ DOĞRU: Contact domain logic içerebilir
@Entity
@Getter
@Setter
@SuperBuilder
public class Contact extends BaseEntity {
    private UUID ownerId;
    private String contactValue;
    // ... fields

    // ✅ Domain logic KALSIN (verify, makePrimary mantıklı!)
    public void verify(String code) {
        if (this.isVerified) {
            throw new IllegalStateException("Already verified");
        }
        if (!code.equals(this.verificationCode)) {
            throw new IllegalArgumentException("Invalid code");
        }
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    public void makePrimary() {
        if (!this.isVerified) {
            throw new IllegalStateException("Cannot make unverified contact primary");
        }
        this.isPrimary = true;
    }
}
```

**Neden Rich Domain?**

- Contact domain logic basit ve self-contained
- verify(), makePrimary() business invariant'lar içeriyor
- Entity bu logic'i içermek için uygun
- User/Company'den FARKLI - Contact daha basit domain

**Seçenek 2: Anemic Domain** ⚠️

```java
// ⚠️ TARTIŞMALI: Contact için anemic olması gerekir mi?
@Entity
@Getter
@Setter
public class Contact extends BaseEntity {
    // Sadece fields
    // verify, makePrimary → ContactService'e taşı
}
```

**KARAR:** **Seçenek 1 - Rich Domain** (Contact için mantıklı)

---

#### C. Mapper Oluştur

```
Hedef:
application/mapper/
├── ContactMapper.java       # DTO ↔ Entity
└── ContactEventMapper.java  # Entity → Event
```

**Aksiyon:**

- [ ] ContactMapper.java oluştur (fromCreateRequest, toResponse, etc.)
- [ ] ContactEventMapper.java oluştur (toCreatedEvent, etc.)
- [ ] Service'deki mapping logic'i Mapper'a taşı
- [ ] Event building logic'i EventMapper'a taşı

**⚠️ DİKKAT:** Entity'deki domain method'ları (verify, makePrimary) TAŞIMA!

---

#### D. Service Temizliği

```java
// Hedef: ContactService sadece orchestration
@Service
public class ContactService {

    @Transactional
    public UUID createContact(CreateContactRequest request, UUID currentUserId) {
        // Owner check (NO POLICY!)
        if (!request.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedException("Cannot create contact for other users");
        }

        // Mapping → Mapper'a delege
        Contact contact = contactMapper.fromCreateRequest(request, currentUserId);
        contact = contactRepository.save(contact);

        // Event → EventMapper'a delege
        eventPublisher.publishContactCreated(
            eventMapper.toCreatedEvent(contact)
        );

        return contact.getId();
    }

    @Transactional
    public void verifyContact(UUID contactId, String code, UUID currentUserId) {
        Contact contact = getContactEntity(contactId);

        // Owner check
        if (!contact.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedException();
        }

        // Domain logic (entity method - KALACAK!)
        contact.verify(code);
        contactRepository.save(contact);

        // Event
        eventPublisher.publishContactUpdated(
            eventMapper.toUpdatedEvent(contact)
        );
    }
}
```

**Aksiyon:**

- [ ] Tüm mapping logic'i kaldır → Mapper'a taşı
- [ ] Event building'i kaldır → EventMapper'a taşı
- [ ] ⚠️ Domain method'ları (verify, makePrimary) KORU!
- [ ] Owner-based authorization KORU (NO POLICY!)
- [ ] Gereksiz comment'leri sil

---

#### E. Controller Temizliği

```java
// Hedef: Controller sadece HTTP handling
@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UUID>> createContact(
            @Valid @RequestBody CreateContactRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {

        UUID contactId = contactService.createContact(request, UUID.fromString(ctx.getUserId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(contactId, "Contact created successfully"));
    }

    @PostMapping("/{contactId}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> verifyContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody VerifyContactRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {

        contactService.verifyContact(contactId, request.getCode(), UUID.fromString(ctx.getUserId()));
        return ResponseEntity.ok(ApiResponse.success(null, "Contact verified successfully"));
    }
}
```

**Aksiyon:**

- [ ] Gereksiz comment'leri sil
- [ ] Log statement'ları basitleştir
- [ ] SecurityContext kullan (@AuthenticationPrincipal)

---

#### F. Gereksiz Sınıfları Kaldır

```
Kontrol et ve sil:
- [ ] ContactValidator (varsa) → Spring @Valid yeterli
- [ ] ContactHelper (varsa) → Private method yap
- [ ] ContactEnricher (varsa) → Mapper'a merge et
- [ ] ContactFactory (varsa) → Entity.create() yeterli
```

---

### 3️⃣ **Doğrulama Aşaması**

#### A. Kod Kalitesi

- [ ] Entity 250 satırın altında mı? (Rich domain için OK)
- [ ] Service'de mapping logic var mı? (Olmamalı!)
- [ ] Controller'da business logic var mı? (Olmamalı!)
- [ ] Comment noise temizlendi mi?
- [ ] ⚠️ Domain method'lar entity'de kaldı mı? (verify, makePrimary)

#### B. Prensip Kontrolü

- [ ] **SRP:** Her sınıf tek sorumluluk mu?
- [ ] **DRY:** Kod tekrarı var mı?
- [ ] **KISS:** Basit mi, over-engineering yok mu?
- [ ] **YAGNI:** Gereksiz abstraction var mı?
- [ ] **Rich Domain:** Entity domain logic içerebilir (Contact için OK)

#### C. Lint & Test

- [ ] `read_lints` ile hata kontrolü yap
- [ ] Import'lar temiz mi?
- [ ] Kullanılmayan import var mı?

---

## 🏆 Başarı Kriterleri

### Hedef Metrikler:

| Metrik               | Hedef                            |
| -------------------- | -------------------------------- |
| **Contact Entity**   | ~200-250 satır (domain logic OK) |
| **ContactService**   | <200 satır (orchestration only)  |
| **Mapper Sayısı**    | 2 mapper (SRP)                   |
| **Over-engineering** | 0 gereksiz sınıf                 |
| **Comment Noise**    | Minimal (sadece WHY)             |
| **LOC Azalması**     | -20% to -40%                     |

---

## ⚠️ CONTACT-SERVICE ÖZELLİKLERİ

### User/Company'den Farkları

1. **Rich Domain Model OK** ✅

   ```
   Contact.verify(code)      → Domain logic (KORU!)
   Contact.makePrimary()     → Domain logic (KORU!)
   Contact.generateVerificationCode() → Domain logic (KORU!)
   ```

2. **No Policy** ❌

   ```
   PolicyEngine KULLANMA!
   CompanyType field EKLEME!
   PolicyContext OLUŞTURMA!

   Basit owner check YETER:
   if (!contact.getOwnerId().equals(currentUserId)) {
       throw new UnauthorizedException();
   }
   ```

3. **Basit Authorization** ✅
   ```
   Owner-based: Contact sahibi işlem yapabilir
   Gateway: JWT validation yeterli
   Service: Owner check yeterli
   ```

---

## ⚠️ YAPMAMANLAR (Anti-Patterns)

### ❌ YAPMA:

1. ❌ Contact.verify() metodunu Service'e taşıma (domain logic!)
2. ❌ Contact.makePrimary() metodunu Service'e taşıma (domain logic!)
3. ❌ Policy field'ları ekleme (CompanyType, etc.)
4. ❌ PolicyEngine integration (gereksiz complexity!)
5. ❌ Yeni validator/helper/builder sınıfı oluşturma
6. ❌ Service'de mapping logic bırakma
7. ❌ Gereksiz comment ekleme
8. ❌ Over-engineering yapma

### ✅ YAP:

1. ✅ Önce dokümantasyonu oku
2. ✅ Mevcut kodu analiz et
3. ✅ Mapping → Mapper'a taşı
4. ✅ Event building → EventMapper'a taşı
5. ✅ Entity domain method'ları KORU (verify, makePrimary)
6. ✅ Service → Sadece orchestration bırak
7. ✅ Comment'leri temizle
8. ✅ Owner-based auth KORU (basit ve yeterli!)
9. ✅ DTO request/response ayrımı yap
10. ✅ Test et ve doğrula

---

## 🎯 Beklenen Sonuç

### Kod Kalitesi:

- ✅ Contact entity: Rich domain (~200-250 satır) - domain logic OK
- ✅ ContactService: Orchestration only (~150-200 satır)
- ✅ 2 Mapper: SRP uygulanmış
- ✅ Clean controllers: Minimal logic
- ✅ Zero over-engineering
- ✅ Self-documenting code
- ✅ **NO POLICY!** (basit kalsın)

### Yapı:

```
contact-service/
├── api/
│   ├── ContactController.java
│   └── dto/
│       ├── request/
│       │   ├── CreateContactRequest.java
│       │   ├── UpdateContactRequest.java
│       │   └── VerifyContactRequest.java
│       └── response/
│           └── ContactResponse.java
│
├── application/
│   ├── mapper/
│   │   ├── ContactMapper.java
│   │   └── ContactEventMapper.java
│   └── service/
│       └── ContactService.java
│
├── domain/
│   ├── aggregate/
│   │   └── Contact.java (Rich domain - verify, makePrimary OK!)
│   ├── event/
│   │   ├── ContactCreatedEvent.java
│   │   ├── ContactUpdatedEvent.java
│   │   └── ContactDeletedEvent.java
│   └── valueobject/
│       └── ContactType.java
│
└── infrastructure/
    ├── repository/
    │   └── ContactRepository.java
    ├── messaging/
    │   └── ContactEventPublisher.java
    └── config/
```

---

## 📝 Rapor Formatı

İşlem tamamlandığında şu formatta rapor ver:

```markdown
## 🎉 Contact-Service Refactoring TAMAMLANDI!

### 📊 Sonuçlar

| Dosya               | ÖNCE | SONRA | İyileştirme |
| ------------------- | ---- | ----- | ----------- |
| Contact.java        | XXX  | XXX   | -XX%        |
| ContactService.java | XXX  | XXX   | -XX%        |

### ✅ Yapılanlar

1. DTO organizasyonu (request/response)
2. Mapper oluşturma (ContactMapper, ContactEventMapper)
3. Service temizliği (mapping → Mapper)
4. Controller temizliği (comment cleanup)
5. Entity: Rich domain preserved (verify, makePrimary)

### 🏆 Uygulanan Prensipler

- SRP, DRY, KISS, YAGNI
- Rich Domain Model (Contact için uygun)
- Mapper Separation
- NO Policy (basit authorization)
- Owner-based access control

### ⚠️ Özel Notlar

- Contact.verify() entity'de kaldı (domain logic!)
- Contact.makePrimary() entity'de kaldı (domain logic!)
- Policy integration YOK (gereksiz complexity)
- Owner-based authorization yeterli
```

---

## 🎓 Contact-Service Özellikleri

### User/Company'den Farklar

| Özellik            | User/Company | Contact | Neden?                                       |
| ------------------ | ------------ | ------- | -------------------------------------------- |
| **Domain Model**   | Anemic       | Rich    | Contact domain logic basit ve self-contained |
| **Entity Methods** | NO           | YES     | verify(), makePrimary() business invariant   |
| **Policy**         | YES          | NO      | Contact basit, owner check yeterli           |
| **Complexity**     | Orta         | Düşük   | Basit domain                                 |

---

## 🎯 Refactoring Checklist

### DTO Organizasyonu

- [ ] api/dto/request/ klasörü
- [ ] api/dto/response/ klasörü
- [ ] CreateContactRequest
- [ ] UpdateContactRequest
- [ ] VerifyContactRequest
- [ ] ContactResponse

### Mapper

- [ ] ContactMapper.java oluştur
- [ ] ContactEventMapper.java oluştur
- [ ] Service'deki mapping logic taşı
- [ ] Event building logic taşı

### Service

- [ ] Mapping logic kaldır
- [ ] Event building kaldır
- [ ] Owner-based auth KORU
- [ ] ❌ Policy EKLEME!
- [ ] Domain method'ları entity'den ÇAĞIRabiliyor (verify, makePrimary)

### Entity

- [ ] Domain method'ları KORU (verify, makePrimary, generateVerificationCode)
- [ ] Factory method KORU (create)
- [ ] Business invariant'ları KORU
- [ ] Gereksiz comment'leri sil

### Controller

- [ ] Comment noise temizle
- [ ] SecurityContext kullan
- [ ] Log basitleştir
- [ ] ❌ Policy check EKLEME!

### Cleanup

- [ ] Gereksiz validator sil
- [ ] Gereksiz helper sil
- [ ] Kullanılmayan import sil
- [ ] Boş klasör sil

---

## 💡 Önemli Hatırlatmalar

### 1. Rich Domain Model (Contact için)

```java
// ✅ DOĞRU: Business logic entity'de kalabilir
public class Contact {
    public void verify(String code) {
        // Validation + state change
        if (!code.equals(this.verificationCode)) {
            throw new IllegalArgumentException("Invalid code");
        }
        this.isVerified = true;
    }
}
```

**Neden:** Contact basit domain, logic self-contained, entity içinde mantıklı

---

### 2. NO Policy!

```java
// ✅ DOĞRU: Basit owner check
if (!contact.getOwnerId().equals(currentUserId)) {
    throw new UnauthorizedException();
}

// ❌ YANLIŞ: Policy kullanma!
PolicyContext ctx = PolicyContext.builder()...  // YAPMA!
```

**Neden:** Contact için owner-based auth yeterli, policy gereksiz complexity

---

### 3. Mapping Logic → Mapper

```java
// ✅ DOĞRU: Mapper'da
public Contact fromCreateRequest(CreateContactRequest request, String createdBy) {
    return Contact.builder()
        .ownerId(request.getOwnerId())
        .contactValue(request.getContactValue())
        // ... mapping
        .build();
}

// ❌ YANLIŞ: Service'de
public UUID createContact(CreateContactRequest request) {
    Contact contact = Contact.builder()
        .ownerId(request.getOwnerId())
        // ... 15 satır mapping! (YAPMA!)
        .build();
}
```

---

## 📚 Referanslar

### Başarılı Örnekler

1. **User-Service Refactoring**

   - Entity: 408 → 99 lines (-76%)
   - Anemic Domain başarısı
   - Dosya: `docs/reports/USER_SERVICE_FINAL_REFACTORING_SUMMARY.md`

2. **Company-Service Refactoring**
   - Entity: 430 → 109 lines (-75%)
   - CQRS kaldırma (22 sınıf!)
   - Dosya: `COMPANY_SERVICE_REFACTORING_COMPLETE.md`

### Contact-Service İçin Örnek

**Contact farklı!**

- Rich Domain Model OK
- Entity ~200-250 satır OK (domain logic var)
- Anemic olmak zorunda değil

---

## 🎯 Success Criteria

### MUST HAVE

- [x] DTO request/response ayrımı
- [x] ContactMapper + ContactEventMapper
- [x] Service'de mapping yok
- [x] Service'de event building yok
- [x] Owner-based auth korunmuş
- [x] **NO POLICY integration**
- [x] Comment noise temiz
- [x] Zero over-engineering

### NICE TO HAVE

- [x] Entity domain method'ları preserved
- [x] LOC reduction -20% to -40%
- [x] Self-documenting code
- [x] Clean git history

---

**Hazırlayan:** User + AI Team  
**Tarih:** 2025-10-10  
**Hedef:** Contact-Service Production Ready  
**Özellik:** Rich Domain Model (User/Company'den farklı)  
**Kural:** NO POLICY! Keep it simple!

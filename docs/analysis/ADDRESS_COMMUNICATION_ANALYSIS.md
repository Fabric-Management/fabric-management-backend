# 📍 Adres & İletişim Bilgisi Yönetimi Analizi

**Tarih:** 2025-11-01  
**Hedef:** User ve Company için kapsamlı adres/iletişim yönetimi  
**Durum:** ⚠️ Mevcut yapı yetersiz, yeni mimari tasarım gerekiyor

---

## 🎯 Gereksinimler

### **User İletişim Bilgileri:**
1. ✅ **Kişisel İletişim Kanalları** (Multiple)
   - Kişisel email (ör: john.doe@gmail.com)
   - Kişisel telefon (ör: +905551234567)
   - WhatsApp numarası (aynı telefon veya farklı)
   - Diğer kanallar (isteğe bağlı)

2. ✅ **Kişisel Adres Bilgileri** (Multiple)
   - Ev adresi
   - İkinci adres (isteğe bağlı)
   - Gönderim adresi (isteğe bağlı)

3. ✅ **İş İletişim Bilgileri** (Company'den sağlanan)
   - İş email'i (ör: john.doe@acme.com)
   - Şirket telefonundan extension/dahili (ör: +90-212-123-4567 ext. 101)
   - Ofis adresi (Company'nin adresinden bağımsız, user'a özel ofis)

### **Company İletişim Bilgileri:**
1. ✅ **Şirket Adresleri** (Multiple)
   - Ana merkez
   - Şube adresleri
   - Depo adresleri

2. ✅ **Şirket İletişim Kanalları** (Multiple)
   - Ana telefon (+90-212-123-4567)
   - Faks
   - Email (info@acme.com, support@acme.com)
   - Website

---

## 📊 Mevcut Durum Analizi

### **✅ Mevcut Yapı:**

#### **User Entity:**
```java
// MEVCUT:
- contactValue (String) → Email veya telefon (TEK DEĞER!)
- contactType (Enum: EMAIL/PHONE) → TEK TİP!
- ❌ Adres bilgisi YOK
- ❌ Multiple iletişim kanalı YOK
- ❌ İş iletişim bilgisi YOK
- ❌ Extension/dahili numara YOK
```

#### **Company Entity:**
```java
// MEVCUT:
- address (String) → TEK ADRES (basit string!)
- city (String)
- country (String)
- phoneNumber (String) → TEK TELEFON
- email (String) → TEK EMAIL
- ❌ Multiple adres YOK
- ❌ Multiple iletişim kanalı YOK
- ❌ Extension yönetimi YOK
```

#### **Communication Module:**
```java
// MEVCUT:
- Sadece notification/verification için
- Contact/address management YOK
- Entity yok, sadece service layer
```

---

## ❌ Eksiklikler

### **1. User Entity Eksiklikleri:**
- ❌ **Multiple Contact Channels:** User'ın birden fazla email/telefon bilgisi yok
- ❌ **Address Management:** Kişisel adres bilgisi hiç yok
- ❌ **Business Contact:** İş email'i ve extension numarası yok
- ❌ **Contact Ownership:** Kişisel vs. şirket sağlamış ayrımı yok

### **2. Company Entity Eksiklikleri:**
- ❌ **Multiple Addresses:** Şirketin birden fazla adresi olamıyor
- ❌ **Multiple Contacts:** Şirketin birden fazla telefon/email'i yok
- ❌ **Address Type:** Adres tipi yok (merkez, şube, depo)
- ❌ **Contact Channels:** Faks, website gibi kanallar yok

### **3. İlişki Eksiklikleri:**
- ❌ **User ↔ Company Contact:** User'ın şirket telefonundan extension'ı yok
- ❌ **User Business Address:** User'ın iş adresi (company address'ten bağımsız) yok
- ❌ **Contact Verification:** İletişim kanallarının doğrulama durumu yok

---

## 🏗️ Önerilen Mimari

### **Yaklaşım: Normalized Contact & Address Management**

#### **Felsefe:**
1. **Contact** ve **Address** ayrı entity'ler olmalı
2. **ContactType** genişletilmeli (EMAIL, PHONE, PHONE_EXTENSION, FAX, WEBSITE, etc.)
3. **UserContact** ve **CompanyContact** ayrı junction table'lar
4. **UserAddress** ve **CompanyAddress** ayrı entity'ler
5. **PhoneExtension** özel bir contact type (parent phone ile ilişkili)

---

### **Entity Tasarımı:**

#### **1. Contact Entity (Generic)**
```java
@Entity
@Table(name = "common_contact")
public class Contact extends BaseEntity {
    private String contactValue;        // "john.doe@acme.com", "+90-212-123-4567"
    private ContactType contactType;    // EMAIL, PHONE, PHONE_EXTENSION, FAX, WEBSITE
    private Boolean isVerified;         // Verification status
    private Boolean isPrimary;         // Primary contact for this owner
    private String label;              // "Home", "Work", "Mobile", "Extension 101"
    private UUID parentContactId;       // For PHONE_EXTENSION → parent phone's contactId
    private Boolean isPersonal;         // true = User's personal, false = Company provided
}
```

#### **2. Address Entity (Generic)**
```java
@Entity
@Table(name = "common_address")
public class Address extends BaseEntity {
    private String streetAddress;       // "123 Main St, Apt 4B"
    private String city;
    private String state;               // İl/state
    private String postalCode;
    private String country;
    private AddressType addressType;    // HOME, WORK, WAREHOUSE, BRANCH, SHIPPING
    private Boolean isPrimary;
    private String label;               // "Headquarters", "Home", "Shipping Address"
}
```

#### **3. UserContact (Junction Table)**
```java
@Entity
@Table(name = "common_user_contact")
public class UserContact {
    @EmbeddedId
    private UserContactId id;           // userId + contactId
    
    @ManyToOne
    private User user;
    
    @ManyToOne
    private Contact contact;
    
    private Boolean isDefault;          // Default contact for notifications
    private Boolean isForAuthentication; // Login için kullanılıyor mu?
}
```

#### **4. CompanyContact (Junction Table)**
```java
@Entity
@Table(name = "common_company_contact")
public class CompanyContact {
    @EmbeddedId
    private CompanyContactId id;
    
    @ManyToOne
    private Company company;
    
    @ManyToOne
    private Contact contact;
    
    private Boolean isDefault;
    private String department;           // "Sales", "Support" için hangi departman
}
```

#### **5. UserAddress (Junction Table)**
```java
@Entity
@Table(name = "common_user_address")
public class UserAddress {
    @EmbeddedId
    private UserAddressId id;
    
    @ManyToOne
    private User user;
    
    @ManyToOne
    private Address address;
    
    private Boolean isPrimary;
    private Boolean isWorkAddress;      // true = İş adresi (Company'den bağımsız)
}
```

#### **6. CompanyAddress (Junction Table)**
```java
@Entity
@Table(name = "common_company_address")
public class CompanyAddress {
    @EmbeddedId
    private CompanyAddressId id;
    
    @ManyToOne
    private Company company;
    
    @ManyToOne
    private Address address;
    
    private Boolean isPrimary;
    private Boolean isHeadquarters;
}
```

---

### **Genişletilmiş ContactType Enum:**
```java
public enum ContactType {
    EMAIL,              // Email address
    PHONE,              // Phone number (mobile/landline)
    PHONE_EXTENSION,    // Extension (parent phone ile ilişkili)
    FAX,                // Fax number
    WEBSITE,            // Website URL
    WHATSAPP,           // WhatsApp Business number
    SOCIAL_MEDIA        // Social media handle
}
```

### **AddressType Enum:**
```java
public enum AddressType {
    HOME,               // Ev adresi
    WORK,               // İş adresi
    HEADQUARTERS,       // Şirket merkez
    BRANCH,             // Şube
    WAREHOUSE,          // Depo
    SHIPPING,           // Gönderim adresi
    BILLING             // Fatura adresi
}
```

---

## 🔄 Migration Stratejisi

### **Phase 1: Backward Compatible Migration**
1. ✅ Yeni `common_contact` ve `common_address` tabloları oluştur
2. ✅ Mevcut `User.contactValue` ve `Company.phoneNumber/email` değerlerini migrate et
3. ✅ `User.contactValue` → `Contact` + `UserContact` ilişkisi
4. ✅ `Company.address` → `Address` + `CompanyAddress` ilişkisi
5. ✅ Eski kolonları `@Deprecated` olarak işaretle (Phase 2'de kaldırılacak)

### **Phase 2: Cleanup**
1. ✅ Eski kolonları kaldır (`User.contactValue`, `Company.address`, etc.)
2. ✅ Tüm kodları yeni entity'lere migrate et

---

## 📋 Yol Haritası

### **Faz 1: Entity & Database (1-2 hafta)**
- [ ] `Contact` entity + repository
- [ ] `Address` entity + repository
- [ ] `UserContact` junction entity + repository
- [ ] `CompanyContact` junction entity + repository
- [ ] `UserAddress` junction entity + repository
- [ ] `CompanyAddress` junction entity + repository
- [ ] Flyway migration scriptleri
- [ ] Mevcut data migration

### **Faz 2: Service Layer (1 hafta)**
- [ ] `ContactService` (CRUD + verification)
- [ ] `AddressService` (CRUD)
- [ ] `UserContactService` (User'a contact ekle/çıkar)
- [ ] `CompanyContactService` (Company'ye contact ekle/çıkar)
- [ ] `UserAddressService` (User'a adres ekle/çıkar)
- [ ] `CompanyAddressService` (Company'ye adres ekle/çıkar)

### **Faz 3: API Layer (1 hafta)**
- [ ] `ContactController` (REST endpoints)
- [ ] `AddressController` (REST endpoints)
- [ ] `UserController` endpoints'e contact/address management ekle
- [ ] `CompanyController` endpoints'e contact/address management ekle

### **Faz 4: Integration & Testing (1 hafta)**
- [ ] Auth service ile entegrasyon (login için contactValue lookup)
- [ ] Communication service ile entegrasyon (notification targeting)
- [ ] Unit testler
- [ ] Integration testler

### **Faz 5: Deprecation & Cleanup (1 hafta)**
- [ ] Eski kolonları kaldır
- [ ] Kod cleanup
- [ ] Documentation update

---

## ⚠️ Kritik Noktalar

### **1. Authentication Uyumluluğu:**
- ✅ **CRITICAL:** Login işlemi hala `contactValue` lookup yapıyor
- ✅ Migration sonrası `User.contactValue` lookup → `UserContact` üzerinden primary contact lookup
- ✅ `isForAuthentication = true` olan contact'ı kullan

### **2. Backward Compatibility:**
- ✅ Phase 1'de eski kolonları deprecated olarak tut
- ✅ Hem eski hem yeni API'ler çalışsın
- ✅ Phase 2'de eski kolonları tamamen kaldır

### **3. Extension Phone Handling:**
- ✅ `PHONE_EXTENSION` contact type'ı özel
- ✅ `parentContactId` ile ana telefon ile ilişkilendir
- ✅ Company'nin telefonundan extension: `CompanyContact.contact` → `Contact.contactValue = "101"`, `parentContactId = companyPhoneContactId`

### **4. Data Integrity:**
- ✅ `Contact` ve `Address` genel entity'ler, tenant isolation gerekiyor
- ✅ Junction table'lar üzerinden user/company filtering
- ✅ Foreign key constraints

---

## 🎯 Öncelik Sırası

1. **YÜKSEK:** User multiple contact channels (iş + kişisel email/phone)
2. **YÜKSEK:** Extension phone support (şirket telefonundan dahili)
3. **ORTA:** User address management (kişisel adresler)
4. **ORTA:** Company multiple addresses (merkez, şube, depo)
5. **DÜŞÜK:** Company multiple contacts (faks, website)

---

## 📝 Notlar

- ✅ **Communication Module:** Notification service'i bu yeni contact entity'leri kullanacak
- ✅ **Verification:** Her contact için verification status takip edilecek
- ✅ **Primary Contact:** Her user/company için bir primary contact belirlenecek
- ✅ **Authentication Contact:** Login için kullanılan contact `isForAuthentication = true` olacak


# Performance Analysis - Log Review

**Date:** 2025-11-12  
**Analysis:** Log anomalies and performance issues

---

## 🔍 Tespit Edilen Sorunlar

### 1. ❌ N+1 Query Problem - CompanyContact

**Sorun:** `CompanyContact` query'si `Contact` entity'lerini fetch etmiyor.

**Log Analizi:**
```
Line 137-160: CompanyContact query (sadece junction entity)
Line 161-185: Aynı query tekrar (duplicate request)
Line 286-310: Aynı query tekrar (3. kez!)
```

**Kod Analizi:**
```java
// CompanyContactRepository.java - Line 23-26
@Query("SELECT cc FROM CompanyContact cc WHERE cc.tenantId = :tenantId AND cc.companyId = :companyId")
List<CompanyContact> findByTenantIdAndCompanyId(...);
// ❌ Contact entity fetch edilmiyor (LAZY loading)
```

**CompanyContactDto.from() çağrıldığında:**
```java
// CompanyContactDto.java - Line 27
.contact(companyContact.getContact() != null ? ContactDto.from(companyContact.getContact()) : null)
// ❌ Her CompanyContact için ayrı Contact query çalışıyor (N+1 problem)
```

**Çözüm:** JOIN FETCH kullanarak Contact entity'lerini tek query'de getirmeli.

---

### 2. ⚠️ Duplicate Requests - Company Contacts Endpoint

**Sorun:** Aynı endpoint 3 kez çağrılıyor.

**Log Analizi:**
```
Line 125: GET /api/common/companies/{companyId}/contacts (1. çağrı)
Line 228: GET /api/common/companies/{companyId}/contacts (2. çağrı)
Line 284: GET /api/common/companies/{companyId}/contacts (3. çağrı)
```

**Olası Sebepler:**
- Frontend'de duplicate request
- React component'lerde multiple useEffect
- Stale closure problemi

**Çözüm:** Frontend'de request deduplication veya caching.

---

### 3. ⚠️ Duplicate Queries - Department Categories

**Sorun:** Department categories query'si 2 kez çalışıyor.

**Log Analizi:**
```
Line 377-397: Department categories query (1. çağrı)
Line 418-439: Department categories query (2. çağrı - duplicate)
```

**Olası Sebep:** Service method'u 2 kez çağrılıyor veya cache çalışmıyor.

---

### 4. ⚠️ Duplicate Queries - Position Query

**Sorun:** Position query'si 2 kez çalışıyor (çok karmaşık query).

**Log Analizi:**
```
Line 533-631: Position query (1. çağrı - çok karmaşık)
Line 632-714: Position query (2. çağrı - duplicate)
```

**Query Karmaşıklığı:**
- 3 LEFT JOIN FETCH (defaultRole, hierarchicalParent, department)
- DISTINCT kullanımı
- Çok fazla column fetch ediliyor

---

### 5. ✅ Role Query - Doğru Çalışıyor

**Log Analizi:**
```
Line 336-374: Role query
Line 375: "Found 8 platform roles (from SYSTEM_TENANT_ID)"
```

**Kod:** RoleService doğru çalışıyor, SYSTEM_TENANT_ID kullanıyor.

---

## 🔧 Çözümler

### 1. ✅ N+1 Problem Çözümü - JOIN FETCH (Uygulandı)

**CompanyContactRepository - Tüm Query'ler Düzeltildi:**
- ✅ `findByTenantIdAndCompanyId` → JOIN FETCH eklendi
- ✅ `findByCompanyIdAndContactId` → JOIN FETCH eklendi
- ✅ `findDefaultByCompanyId` → JOIN FETCH eklendi
- ✅ `findByCompanyIdAndDepartment` → JOIN FETCH eklendi

**UserContactRepository - Tüm Query'ler Düzeltildi:**
- ✅ `findByTenantIdAndUserId` → JOIN FETCH eklendi
- ✅ `findByUserIdAndContactId` → JOIN FETCH eklendi
- ✅ `findDefaultByUserId` → JOIN FETCH eklendi
- ✅ `findPreferredContactByUserId` → JOIN FETCH eklendi
- ✅ `findByTenantIdAndContactId` → JOIN FETCH eklendi

**Örnek Düzeltme:**
```java
// ÖNCE (N+1 Problem)
@Query("SELECT cc FROM CompanyContact cc WHERE cc.tenantId = :tenantId AND cc.companyId = :companyId")
List<CompanyContact> findByTenantIdAndCompanyId(...);

// SONRA (JOIN FETCH)
@Query("SELECT cc FROM CompanyContact cc " +
       "LEFT JOIN FETCH cc.contact " +
       "WHERE cc.tenantId = :tenantId AND cc.companyId = :companyId")
List<CompanyContact> findByTenantIdAndCompanyId(...);
```

**Fayda:**
- ✅ 1 query yerine N+1 query → Tek query
- ✅ Performance artışı (özellikle çok contact varsa)
- ✅ Database load azalması

---

## 📊 Performance İyileştirmeleri

### Önce (N+1 Problem):
```
1 query: CompanyContact listesi (N kayıt)
N query: Her CompanyContact için Contact entity
Total: 1 + N query
```

### Sonra (JOIN FETCH):
```
1 query: CompanyContact + Contact (JOIN FETCH)
Total: 1 query
```

**Örnek:** 10 company contact varsa
- Önce: 1 + 10 = 11 query
- Sonra: 1 query
- İyileştirme: %91 azalma

---

## 🎯 Öncelik Sırası

1. **🔴 Yüksek Öncelik:** N+1 problem çözümü (JOIN FETCH)
2. **🟡 Orta Öncelik:** Duplicate request analizi (frontend)
3. **🟢 Düşük Öncelik:** Department categories duplicate query

---

## Sonuç

**Tespit Edilen Sorunlar:**
1. ❌ N+1 Query Problem (CompanyContact → Contact)
2. ⚠️ Duplicate Requests (3 kez aynı endpoint)
3. ⚠️ Duplicate Queries (Department categories, Position)

**Önerilen Çözümler:**
1. ✅ JOIN FETCH ekle (CompanyContactRepository)
2. ✅ Frontend'de request deduplication
3. ✅ Cache kontrolü (Department categories)


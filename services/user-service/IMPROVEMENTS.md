# 🚀 User Service - İyileştirme Planı

**Tarih:** 8 Ekim 2025  
**Durum:** Planlama Aşaması  
**Öncelik:** Performans ve Organizasyon

---

## 🔥 Yüksek Öncelikli (High Priority)

### 1. Batch Contact Fetching API (Performance - Kritik)

**Sorun:**

- `UserMapper.toResponseList()` N+1 query problemi
- 100 user = 100 Contact Service API call
- Response time: 5+ saniye (100 user için)

**Çözüm:**

1. Contact Service'e yeni endpoint ekle:

   ```
   POST /api/v1/contacts/batch/by-owners
   Body: ["user-id-1", "user-id-2", ...]
   Response: Map<String, ContactInfo>
   ```

2. UserMapper'da batch fetching implement et:

   ```java
   public List<UserResponse> toResponseList(List<User> users) {
       List<String> userIds = users.stream()
           .map(u -> u.getId().toString())
           .toList();

       Map<String, ContactInfo> contacts =
           contactServiceClient.getContactsBatch(userIds);

       return users.stream()
           .map(user -> toResponse(user, contacts.get(user.getId())))
           .toList();
   }
   ```

**Beklenen İyileştirme:**

- 100 user = 1 API call
- Response time: 500ms (90% iyileşme!)

**Tahmini Süre:** 4 saat

---

### 2. Email Search Implementation

**Sorun:**

- `UserService.searchUsers()` email parametresi disabled
- Email ile arama yapılamıyor

**Çözüm:**

1. Batch contact API implement edildikten sonra
2. Email search'ü aktif et
3. Batch fetching kullan

**Beklenen İyileştirme:**

- Email search çalışır hale gelir
- Performanslı arama

**Tahmini Süre:** 1 saat (Batch API'den sonra)

---

## ⚠️ Orta Öncelikli (Medium Priority)

### 3. UserSearchService Oluştur (SRP)

**Sorun:**

- `UserService.searchUsers()` method UserService içinde
- Search logic business logic ile karışık
- Service 300 satır (hedef: 200)

**Çözüm:**

1. Yeni dosya: `application/service/UserSearchService.java`
2. searchUsers methodunu taşı
3. İleri seviye search logic burada topla

**Beklenen İyileştirme:**

- UserService: 300 → 220 satır
- Better SRP
- Easier testing

**Tahmini Süre:** 2 saat

---

### 4. Database Query-Based Filtering

**Sorun:**

- `searchUsers()` in-memory filtering yapıyor
- Stream API kullanımı (tüm data memory'ye çekiliyor)
- Büyük dataset'lerde performans problemi

**Çözüm:**

1. UserRepository'de dynamic query method:

   ```java
   @Query("SELECT u FROM User u WHERE " +
          "(:firstName IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) AND " +
          "(:lastName IS NULL OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) AND " +
          "(:status IS NULL OR u.status = :status) AND " +
          "u.tenantId = :tenantId AND u.deleted = false")
   List<User> searchUsers(
       @Param("tenantId") UUID tenantId,
       @Param("firstName") String firstName,
       @Param("lastName") String lastName,
       @Param("status") String status
   );
   ```

2. Specification Pattern kullan (daha esnek)

**Beklenen İyileştirme:**

- Database-level filtering
- Sadece gerekli data çekilir
- Memory kullanımı azalır

**Tahmini Süre:** 3 saat

---

## ✨ Düşük Öncelikli (Low Priority)

### 5. Caching Layer (Redis)

**Sorun:**

- Her request'te database'e gidiliyor
- Sık okunan data için gereksiz query

**Çözüm:**

1. UserMapper'da contact cache:

   ```java
   @Cacheable(value = "user-contacts", key = "#userId")
   public ContactInfo getContactInfo(UUID userId) {
       // Contact Service call
   }
   ```

2. UserService'de user cache:
   ```java
   @Cacheable(value = "users", key = "#userId + '-' + #tenantId")
   public UserResponse getUser(UUID userId, UUID tenantId) {
       // ...
   }
   ```

**Beklenen İyileştirme:**

- Read-heavy operations %80 faster
- Database load azalır

**Tahmini Süre:** 4 saat

---

### 6. Pagination Support

**Sorun:**

- `listUsers()` ve `searchUsers()` tüm data'yı döndürüyor
- 1000+ user'da performans problemi

**Çözüm:**

1. Pageable parametre ekle
2. Page<User> döndür
3. ApiResponse.paginated() kullan

**Beklenen İyileştirme:**

- Kontrollü data transfer
- Better UX

**Tahmini Süre:** 2 saat

---

## 📝 Notlar

### İmplement Sırası:

1. **Önce:** Batch Contact API (en kritik!)
2. **Sonra:** Email search aktif et
3. **Sonra:** Database query filtering
4. **En Son:** Cache ve pagination

### Performans Hedefleri:

- listUsers (100 user): 5000ms → 500ms
- searchUsers: 6000ms → 600ms
- Cache hit oranı: %70+

### Test Gereksinimleri:

- Unit test: Batch fetching logic
- Integration test: Contact Service batch endpoint
- Performance test: 100, 500, 1000 user scenarios

---

**Not:** Bu iyileştirmeler şu an **acil değil**. Sistem çalışıyor, sadece performans optimize edilecek. Production'a geçtikten sonra, kullanıcı sayısı arttıkça implement edilebilir.

---

**Hazırlayan:** AI Code Architect  
**Güncelleme:** Her sprint sonrası  
**Takip:** Bu dosya güncel tutulacak

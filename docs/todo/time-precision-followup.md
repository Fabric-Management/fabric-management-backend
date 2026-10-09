# TIME-PRECISION-2: doğrudan `Instant.now()` kullanımları

TIME-PRECISION-1 uygulamanın enjekte edilen saatini (`TimeConfig.systemClock()`) PostgreSQL'in
sakladığı hassasiyete, mikrosaniyeye sınırladı. Testlerin `MutableClock`'u da aynı hassasiyette
işliyor. Bu düzeltme yalnız `Clock` bean'ini alan kodu kapsar. `Instant.now()` doğrudan çağrılan
yerler sistem saatinin çözünürlüğünü korur (Linux'ta nanosaniye).

## Neden önemli

Bir zaman bellekte üretilip yazıldıktan sonra, aynı istekte bellekteki değerle yanıtlanırsa ve
sonradan satırdan okunursa, Linux'ta iki farklı değer görünür. Örneğin `…704264232Z` ve
`…704264Z`. macOS'un saati mikrosaniye hassasiyetinde olduğu için fark yerelde çıkmaz, yalnız
CI'da (Linux) çıkar. CEDIT-07 L19 bu yüzden ilk kez CI'da kırıldı.

## Durum (2026-10-09)

`src/main/java` içinde 106 dosyada 216 doğrudan `Instant.now()` çağrısı var. Hepsi aynı risk
değil:

| Öncelik | Ne | Neden |
|---|---|---|
| 1 | `BaseEntity` (`@PrePersist` / `@PreUpdate`: `createdAt`, `updatedAt`, `deletedAt`) | Her entity'de. Kayıttan hemen sonra DTO'ya giden değer nanosaniye, sonraki okuma mikrosaniye. |
| 2 | Yazılıp aynı yanıtta dönen domain zamanları (auth token'ları, davetler, abonelik, flowboard görevleri vb.) | Aynı tutarsızlık. Tek tek bakılmalı. |
| 3 | Event zamanları (`DomainEvent` ve event dinleyicileri) | Saklanıyorsa 2 ile aynı. Sadece bellekte kalıyorsa sorun yok. |
| — | `ApiResponse.timestamp`, `RateLimitAspect`, `HealthController` | Saklanmıyor, satırla karşılaştırılmıyor. Tutarlılık sorunu yok. Saat kaynağı birliği için ileride dönüştürülebilir. |

## Öneri

- `BaseEntity` bir Spring bean'i değil, `Clock` enjekte edilemez. Seçenekler: callback'te
  `truncatedTo(ChronoUnit.MICROS)`, ya da uygulama saatini statik olarak sunan küçük bir
  tutucu (holder). Karar ayrı verilmeli.
- Servislerde `Instant.now()` yerine enjekte edilen `Clock` kullanılmalı. Bu, testlerde saatin
  ileri alınabilmesini de sağlar.
- Dönüşüm bitince `src/main/java` içinde `Instant.now()` kullanımını yasaklayan bir kural
  (ArchUnit ya da Checkstyle) eklenmeli, yeni kullanımlar geri gelmesin.
- Her dönüştürülen alan için "ilk yanıt = satırdan okunan" regresyonu, nanosaniyelik sabit bir
  saatle (`Clock.fixed(…plusNanos(…))`) yazılmalı. Böylece hata macOS'ta da yakalanır.

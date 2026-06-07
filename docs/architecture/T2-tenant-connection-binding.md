# T2 — Her Transaction'da Bağlantıya Tenant Binding (`SET app.current_tenant`)

> RLS politikaları `current_setting('app.current_tenant')` okur. Bu değişken her DB
> bağlantısında, doğru tenant ile set edilmeli; havuza geri dönerken sızdırmamalı.
> T2 bunu **Hibernate'in native bağlantı sağlayıcısıyla** yapar — uygulama kodu hiçbir
> yerde `SET` yazmaz, izolasyon altyapıya gömülüdür.
>
> **Bağımlılık:** T1 (rol ayrımı) merge. **Çıktı:** T3'teki RLS politikalarını fiilen
> "konuşturan" mekanizma. T2 + T3 birlikte izolasyonu canlı eder.

---

## Amaç

Her Hibernate oturumunun kullandığı bağlantıya, `TenantContext`'teki tenant ile
`app.current_tenant` session değişkenini bağlamak; bağlantı havuza dönerken sıfırlamak.
Böylece T3'ün RLS politikaları her sorguda doğru tenant'ı görür ve `fabric_app`
(NOBYPASSRLS) otomatik filtrelenir.

İki ilke:
- **Okuma — deny-by-default:** Context yoksa değişken SYSTEM sentinel'e set edilir →
  RLS yalnızca system satırlarını eşler → tenant verisi görünmez (sıfır satır, sızıntı yok).
- **Yazma — fail-closed:** `BaseEntity.@PrePersist` artık `requireTenantId()` kullanır →
  context'siz yazım sessizce SYSTEM'e düşmek yerine **hata fırlatır**.

---

## Mevcut Durum (Kod Araştırması)

| Bileşen | Dosya | Durum / Etki |
|---------|-------|--------------|
| Tenant set noktası (request) | [JwtContextInterceptor.java](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/java/com/fabricmanagement/common/infrastructure/web/JwtContextInterceptor.java) | `preHandle` tenant'ı **tx'ten önce** set eder → transaction çalışırken context hazır ✅ |
| Async propagasyon | [TenantAwareTaskDecorator.java](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/java/com/fabricmanagement/common/infrastructure/config/TenantAwareTaskDecorator.java) | Thread-local context'i async thread'e taşır → async tx'ler de binding alır ✅ |
| Scheduled iterasyon | [InvoiceOverdueJob.java](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/java/com/fabricmanagement/finance/invoice/app/scheduler/InvoiceOverdueJob.java) | `executeInTenantContext(tenant, ...)` kalıbı → her tenant için context kurulu ✅ |
| Yazım yolu | [BaseEntity.java#L188](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/java/com/fabricmanagement/common/infrastructure/persistence/BaseEntity.java) | `onCreate` `getCurrentTenantId()` (sessiz SYSTEM fallback) kullanıyor → **değişecek** |
| Context API | [TenantContext.java](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/java/com/fabricmanagement/common/infrastructure/persistence/TenantContext.java) | `requireTenantId()`, `getCurrentTenantIdOrNull()`, `SYSTEM_TENANT_ID` hazır ✅ |
| AOP altyapısı | `PolicyCheckAspect`, `RateLimitAspect` | spring-aop classpath'te; mevcut aspect'ler var (alternatif tasarım için referans) |
| JPA | `application.yml` | `open-in-view: false`, `ddl-auto: validate` → bağlantı tx'e bağlı, schema-validate ayrı yoldan ✅ |
| Hibernate multi-tenancy | — | **Hiç yapılandırılmamış** → temiz başlangıç |

Çıkarım: Context her yolda (request/async/scheduled) zaten doğru kuruluyor. Eksik tek
halka, bu context'i **DB bağlantısına** taşımak. Hibernate'in `MultiTenantConnectionProvider`
mekanizması tam bunun için var ve AOP ordering sorunlarından kaçınır.

---

## Proposed Changes

### Tasarım kararı: Hibernate `MultiTenantConnectionProvider` (önerilen)

AOP aspect yerine Hibernate'in native bağlantı sağlayıcısını kullanıyoruz çünkü:
- **Ordering sorunu yok.** Aspect'i "transaction'ın içinde çalış" diye sıralamak
  (tx advisor order'ını değiştirmek) mevcut `PolicyCheckAspect`/`RateLimitAspect`
  davranışını bozabilir. MTCP bu riski tamamen ortadan kaldırır.
- **Tam kapsama.** Yalnızca `@Transactional` metotları değil, **her Hibernate oturumu**
  (read dahil) otomatik kapsanır.
- **Sıfırlama garantili.** Bağlantı havuza dönerken `releaseConnection`'da reset edilir →
  pooled connection sızıntısı yok.

#### [NEW] `TenantConnectionProvider` (binding + reset)

```java
@Component
public class TenantConnectionProvider
    implements MultiTenantConnectionProvider<String>, HibernatePropertiesCustomizer {

  private final DataSource dataSource;
  TenantConnectionProvider(DataSource dataSource) { this.dataSource = dataSource; }

  // Tenant'sız bağlantı (startup, schema validate) — değişken set EDİLMEZ
  @Override public Connection getAnyConnection() throws SQLException { return dataSource.getConnection(); }
  @Override public void releaseAnyConnection(Connection c) throws SQLException { c.close(); }

  // Tenant'lı bağlantı — app.current_tenant SET edilir (session-level, parametreli → injection yok)
  @Override public Connection getConnection(String tenantId) throws SQLException {
    Connection c = getAnyConnection();
    try (PreparedStatement ps =
        c.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
      ps.setString(1, tenantId);
      ps.execute();
    }
    return c;
  }

  // Havuza dönerken RESET — sızıntı önlenir
  @Override public void releaseConnection(String tenantId, Connection c) throws SQLException {
    try (Statement st = c.createStatement()) {
      st.execute("SELECT set_config('app.current_tenant', NULL, false)");
    } finally {
      c.close();
    }
  }

  @Override public boolean supportsAggressiveRelease() { return false; }
  @Override public boolean isUnwrappableAs(Class<?> u) { return false; }
  @Override public <T> T unwrap(Class<T> u) { return null; }

  @Override public void customize(Map<String, Object> props) {
    props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
  }
}
```

#### [NEW] `TenantIdentifierResolver` (context → tenant id)

```java
@Component
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

  @Override public String resolveCurrentTenantIdentifier() {
    UUID t = TenantContext.getCurrentTenantIdOrNull();
    // Context yoksa SYSTEM sentinel → okuma deny-by-default (yazım BaseEntity'de fail-closed)
    return (t != null ? t : TenantContext.SYSTEM_TENANT_ID).toString();
  }

  @Override public boolean validateExistingCurrentSessions() { return true; }

  @Override public void customize(Map<String, Object> props) {
    props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
```

> Her iki bean de `HibernatePropertiesCustomizer` implemente ettiği için Spring Boot
> (3.2 / Hibernate 6.4) bunları otomatik toplar; ekstra config sınıfı gerekmez.
> Hibernate 6'da provider + resolver verilmesi connection-based multi-tenancy'yi etkinleştirir.

#### [CHANGE] `BaseEntity.onCreate` — yazımda fail-closed

```java
@PrePersist
protected void onCreate() {
  if (this.tenantId == null) {
    // ÖNCE: getCurrentTenantId() → context yoksa sessizce SYSTEM_TENANT_ID
    // SONRA: requireTenantId() → context yoksa IllegalStateException (fail-closed)
    this.tenantId = TenantContext.requireTenantId();
  }
  // ... uid/timestamps aynı
}
```

> [!NOTE]
> **WITH CHECK hizalaması (kritik tutarlılık):** Hem `app.current_tenant` (resolver) hem
> `entity.tenant_id` (@PrePersist) aynı `TenantContext`'ten okur. Dolayısıyla T3'ün
> `WITH CHECK (tenant_id = current_setting('app.current_tenant'))` koşulu her zaman tutar.
> Bir servis entity'nin `tenantId`'sini elle farklı bir tenant'a set ederse DB seviyesinde
> reddedilir — ücretsiz bir savunma katmanı.

---

### Etkileşim notları

- **T4 (system yolu):** `runAsSystem` ayrı owner (BYPASSRLS) datasource'u kullanacağı için
  o oturumlarda `app.current_tenant` önemsiz. Aynı session factory üzerinden giderse resolver
  SYSTEM döndürür. Detay T4'te netleşir; T2 bunu bozmaz.
- **T7 (async/scheduled) kapsamı küçülür:** MTCP her Hibernate oturumunu kapsadığı ve context
  zaten async/scheduled thread'lere taşındığı için, T7 büyük ölçüde "zaten çalışıyor"a döner;
  T7 yalnızca doğrulama + outbox publisher kalıbı olarak kalır.
- **Performans:** Oturum başına bir `SET` + bir `RESET` (iki hafif round-trip). RLS izolasyonu
  için kabul edilebilir maliyet; gerekirse ileride tx-local optimizasyonu (Open Question Q1).

---

## Alternatif (reddedilen): AOP `set_config` aspect'i

`@Transactional` metotlarını saran, ilk iş olarak `set_config('app.current_tenant', :t, true)`
(transaction-local) çalıştıran bir aspect. **Reddedilme nedeni:** Aspect'in transaction'ın
*içinde* çalışması için tx advisor order'ının değiştirilmesi gerekir; bu, mevcut
`PolicyCheckAspect`/`RateLimitAspect` sıralamasını etkileyebilir. Ayrıca yalnızca
`@Transactional` yolları kapsanır, native/non-tx Hibernate erişimi açıkta kalır. MTCP her
ikisini de çözer.

---

## User Review Required

> [!IMPORTANT]
> **Q2 — Context yokken okuma davranışı (ana karar):**
> - **Seçenek A (önerilen):** Resolver SYSTEM sentinel döndürür → okuma deny-by-default
>   (sıfır tenant satırı), yazım `BaseEntity.requireTenantId()` ile fail-closed.
>   *Request yolu her zaman context taşıdığı için (interceptor) bu fallback yalnızca
>   context'siz arka plan işlerinde tetiklenir — ADR'nin "request yolunda sessiz fallback yok"
>   ilkesi korunur.*
> - **Seçenek B:** Resolver context yoksa `IllegalStateException` fırlatır (hem okuma hem
>   yazma sert hata). Daha katı ama henüz context kurmayan meşru system/bootstrap yollarını
>   kırabilir (T4 tamamlanana dek riskli).
> Önerim **A** — okuma güvenli (sızıntı yok), yazım sert; T4 sonrası B'ye sıkılaştırılabilir.

> [!IMPORTANT]
> **Q3 — `BaseEntity.onCreate` → `requireTenantId()`:** Bu değişiklik, bugüne dek sessizce
> SYSTEM'e düşen context'siz yazımları **patlatır**. Bu bilinçli (latent bug'ları yüzeye
> çıkarır). Onboarding/seed gibi meşru system yazımları context'i SYSTEM'e açıkça set etmeli
> (T4 ile hizalı). Onaylıyor musunuz?

## Open Questions

> [!NOTE]
> **Q1 — session-level SET + RESET vs transaction-local:** Şu an `set_config(..., false)`
> (session) + release'de reset. Alternatif `set_config(..., true)` (tx-local, otomatik reset,
> reset round-trip'i yok) ama tx dışı autocommit erişiminde değişken bir sonraki statement'a
> taşınmaz. Repository okumaları transactional olduğundan tx-local da çalışırdı; coverage için
> session+reset seçildi. Onay / tercih?

> [!NOTE]
> **Q4 — Spring Boot otomatik bean algılama:** `MultiTenantConnectionProvider` bean'ini Spring
> Boot bazı sürümlerde otomatik wire eder; `HibernatePropertiesCustomizer` ile explicit kayıt
> en güvenli yol (sürümden bağımsız). Bu yaklaşımı koruyalım mı?

---

## Verification Plan

### Otomatik (Testcontainers, `fabric_app` ile bağlanarak)

```java
// 1. Binding: context A iken bağlantı app.current_tenant = A taşıyor
TenantContext.setCurrentTenantId(A);
// → native sorgu: SELECT current_setting('app.current_tenant', true) == A

// 2. Sıfırlama (sızıntı yok): A oturumu biter, B oturumu başlar → değişken B (A değil)

// 3. Yazım fail-closed: context YOKKEN entity persist → IllegalStateException

// 4. Okuma deny-by-default: context YOKKEN tenant tablosu sorgusu → 0 satır (tüm satırlar değil)

// 5. WITH CHECK hizası: context A iken tenantId=B set edilmiş entity persist → DB reddeder
```

### Entegrasyon
- Mevcut tüm IT'ler hâlâ geçiyor (MTCP context'li yollarda şeffaf).
- `JwtContextInterceptor` → tenant → MTCP binding zinciri uçtan uca çalışıyor (request testi).
- Async bir `@Async` listener'da binding doğru tenant'la oluşuyor.

### Manuel
```sql
-- App üzerinden bir istek sonrası, log'da veya bir debug sorgusunda:
SELECT current_setting('app.current_tenant', true);  -- istek tenant'ı görünmeli, sonra reset
```

---

## Definition of Done

- [ ] `TenantConnectionProvider` + `TenantIdentifierResolver` bean'leri kayıtlı; Hibernate
      connection-based multi-tenancy aktif.
- [ ] Her tenant'lı Hibernate oturumu `app.current_tenant`'ı set ediyor; release'de reset ediyor.
- [ ] `getAnyConnection` (startup/schema) yolu değişken set etmiyor (validate bozulmuyor).
- [ ] `BaseEntity.onCreate` `requireTenantId()` kullanıyor (yazımda fail-closed).
- [ ] **Q2** kararı uygulandı (A: deny-by-default okuma / B: sert hata).
- [ ] 5 doğrulama senaryosu (binding, reset, yazım-fail, okuma-deny, WITH CHECK) yeşil.
- [ ] Mevcut IT'ler kırılmadı; request + async zincirleri uçtan uca çalışıyor.

---

## Sonraki Görev

```
T1 ✅ (rol ayrımı)
   └─> T2 (bu görev) — bağlantıya tenant binding (MTCP)
          └─> T3 — tüm tenant-scoped tablolara RLS politikası (FORCE kararı hosting'e bağlı)
                 ├─> T4 — system/bypass yolu (runAsSystem + owner datasource)
                 ├─> T5 — tam izolasyon kanıt testleri
                 ├─> T6 — CI korkuluğu (RLS-eksik tablo = build fail)
                 └─> T7 — async/scheduled doğrulama + outbox kalıbı (MTCP ile kapsamı küçüldü)
```

T2 + T3 birlikte merge edilince izolasyon **canlı** olur: `fabric_app` bağlantısı her sorguda
doğru tenant'ı taşır, RLS politikaları filtreler. T3'ün tek açık kararı FORCE↔BYPASSRLS
(hosting'e bağlı, T1/D5'te ertelendi).

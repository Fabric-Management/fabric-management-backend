# ADR 001: RLS Tabanlı Tenant İzolasyonu Sözleşmesi

## Durum
Kabul Edildi — 2026-06-02

## Referanslar
- [P0-rls-tenant-isolation-tasks.md](../P0-rls-tenant-isolation-tasks.md) — Görev planı
- [T1-app-db-role-separation.md](../T1-app-db-role-separation.md) — T1 detay dokümanı

## Bağlam ve Sorun
Mevcut durumda tenant izolasyonu, uygulama katmanında `BaseEntity` (@PrePersist) ve Hibernate/JPA sorgularına manuel `tenant_id` eklenerek sağlanmaktadır. Ancak bu yaklaşım "insan/kod disiplinine" dayalıdır. Bir geliştiricinin veya otomatik bir kod oluşturucunun native bir sorgu yazarken veya Hibernate tarafında filtreyi atlaması durumunda çapraz-tenant veri sızıntısı yaşanabilir. Veritabanı (PostgreSQL) seviyesinde izolasyon sağlayan 169 tablonun sadece 3'ünde Row-Level Security (RLS) politikası bulunmakta ve bu politikalar mevcut tutarsız yapı (`app.tenant_id` vs `app.current_tenant`) ve eksik veritabanı rolü yapılandırması (uygulamanın veritabanı sahibi rolüyle bağlanması) nedeniyle fiilen bypass edilmektedir. İzolasyonun insana değil, veritabanı motoruna dayandırılması gerekmektedir.

## Kararlar (Kanonik Kurallar)

### Karar 1 — Strateji: Paylaşımlı Şema + RLS
- Mevcut 23 şemalık yapı korunur.
- Her `tenant_id` taşıyan tabloya PostgreSQL RLS politikası eklenecektir.

### Karar 2 — Tek Session Değişkeni: `app.current_tenant`
- Tüm tenant izolasyon politikaları tek bir PostgreSQL custom configuration parameter kullanacaktır: `app.current_tenant` (UUID tipinde).
- Geçmişte kullanılan `app.tenant_id` tamamen terk edilir.

### Karar 3 — Politika Şekli: ENABLE + FORCE + USING + WITH CHECK
Tüm tenant-scoped tablolar için RLS şu formda uygulanacaktır:
```sql
ALTER TABLE schema.table_name ENABLE ROW LEVEL SECURITY;
ALTER TABLE schema.table_name FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_tenant_isolation ON schema.table_name
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);
```
- `FORCE ROW LEVEL SECURITY`: Tablo sahibi bile politikalara tabi olur.
- `USING`: Okuma işlemlerini sınırlar.
- `WITH CHECK`: Yazma (INSERT/UPDATE) işlemlerini sınırlar.
- Politika adı standardı: `rls_tenant_isolation`.

### Karar 4 — Deny-by-Default (DB + Uygulama Katmanı)
*DB Katmanı:*
- `current_setting('app.current_tenant', true)` NULL döndüğünde (yani set edilmediğinde), politikadaki `NULL::uuid = tenant_id` koşulu **false** döner ve hiçbir satır okunamaz/yazılamaz. Bu `missing_ok = true` parametresi ile sağlanır.

*Uygulama Katmanı:*
- İstek-kapsamlı bir işlemde tenant context yoksa sert bir hata (exception) fırlatılmalıdır.
- `TenantContext.getCurrentTenantId()` metodunun sessizce `SYSTEM_TENANT_ID` dönmesi (fallback) kaldırılacaktır; `requireTenantId()` kullanılmalıdır.
- **Tehlike:** `BaseEntity.@PrePersist` metodunun `getCurrentTenantId()` kullanması, context yokken `SYSTEM_TENANT_ID` ile yetim/yanlış-tenant kayıtların oluşmasına yol açabilir. Bu T2'de düzeltilecektir.

### Karar 5 — System / Cross-Tenant Yolu: Ayrıcalıklı DB Rolü
Tenant sınırlarını aşması gereken meşru durumlar (onboarding, system-wide scheduled jobs, seed data) için **sentinel tenant UUID kullanılmayacaktır**. 
- **Gerekçe:** Session değişkeni, app rolünün kendisi tarafından set edilebilir (SET LOCAL). Sentinel-tenant izolasyonu yine "app kodu sihirli UUID'yi set etmesin" disiplinine bağlar — tam da yok etmeye çalıştığımız insan/kod disiplini. BYPASSRLS bir role aittir, değişkene değil; normal istek yolu owner bağlantısını alamadığı için filtreyi bypass edemez.

Bunun yerine:
- Ayrıcalıklı `fabric_owner` rolü ile bağlanan ayrı bir DataSource (`systemDataSource`) oluşturulacaktır.
- `AbstractRoutingDataSource` (ThreadLocal ile bağlantı yönlendirme) kullanılmaz — ayrı, adanmış bir bean tercih edilir. Routing, ThreadLocal manipülasyonuyla değil, hangi TransactionManager'ın inject edildiğiyle belirlenir.
- Uygulama, `@Transactional("systemTxManager")` aracılığıyla, kontrollü ve audit edilebilir bir `runAsSystem()` metodu üzerinden bu bağlantıyı kullanacaktır.
- Normal HTTP istek yolu (app rolü) `BYPASSRLS` yetkisine sahip olmadığı için hiçbir durumda filtreyi atlayamaz.

### Karar 6 — DB Rol Ayrımı: `fabric_owner` + `fabric_app`
Greenfield projemiz için rolleri açık ve anlaşılır kılmak adına:
- `fabric_owner` (mevcut `fabric_user` rename edilerek): DDL, Flyway migration ve system operasyonları içindir. `BYPASSRLS` yetkisine sahiptir, `NOSUPERUSER`'dır ve tabloların sahibidir.
- `fabric_app` (yeni rol): Runtime (HTTP istek) yolu için kullanılır. `NOSUPERUSER`, `NOBYPASSRLS`'dir. Tabloların sahibi değildir, sadece tablolar üzerinde veri işlemi yetkilerine (SELECT/INSERT/UPDATE/DELETE vb.) sahiptir.

**Önemli Bağımlılık (FORCE RLS × BYPASSRLS):**
`FORCE ROW LEVEL SECURITY` komutu uygulandığında, tablo sahibi olan rol bile `BYPASSRLS` yetkisi yoksa, RLS politikasına takılır. Veri göçlerini içeren Flyway DML betiklerinin (ör. `V...__backfill...sql`) çalışabilmesi için `fabric_owner` rolünün `BYPASSRLS` yetkisine sahip olması şarttır.

### Karar 7 — `common_tenant` Tablosu: PK-Tabanlı Self-Policy
Tenant tablosunun (kendisini içeren tablo) RLS politikası dışarıda bırakılmak (exclude) yerine, satırın kendisini kendi birincil anahtarı (ID) üzerinden kontrol edecek özel bir RLS kuralıyla korunacaktır:
```sql
CREATE POLICY rls_tenant_self ON common_tenant.common_tenant
    FOR ALL
    USING (id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (id = current_setting('app.current_tenant', true)::uuid);
```
Meşru cross-tenant erişimler zaten system rolü (Karar 5) kullanılarak yapılacaktır.

**İstisna:**
- `public.event_publication` tablosu (Spring Modulith tarafından yönetilen ve tenant_id içermeyen tablo) açıkça RLS politikaları dışında tutulacaktır. (Not: Event payload'ları asenkron işlem için `tenantId` taşıyacaktır).

## Ek Notlar (T2 İmplementasyon Kuralları)
- **Boş String Tuzağı:** PostgreSQL'de `set_config('app.current_tenant', '', true)` çağrısı boş bir string'i `uuid`'ye cast etmeye çalıştığı için istisna fırlatır. T2 uygulanırken, değer varsa gerçek bir UUID atanmalı, yoksa boş string değil, `set_config` tamamen bypass edilmeli (veya NULL temizliği yapılmalıdır).

### Karar 8 — Production Hosting ve BYPASSRLS Teyidi
**Karar tarihi:** 2026-06-06

Plan B (FORCE ROW LEVEL SECURITY + BYPASSRLS ayrıcalıklı rol) production'da varsayılan stratejidir.

**Desteklenen Hosting Ortamları:**
| Ortam | BYPASSRLS | Durum |
|-------|-----------|-------|
| Self-hosted PostgreSQL | ✅ Tam destek | Önerilen |
| AWS RDS | ✅ `rds_superuser` üzerinden | Desteklenir |
| Google Cloud SQL | ✅ `cloudsqlsuperuser` üzerinden | Desteklenir |
| Neon | ✅ `neondb_owner` üzerinden | Desteklenir |
| Supabase Free/Pro | ❌ Rol yönetimi kısıtlı | **Desteklenmez** — Plan A'ya (FORCE yok, sadece ENABLE) dönüş gerekir |

**Rol matrisi (production):**
| Rol | Yetki | Kullanım |
|-----|-------|----------|
| `fabric_owner` | DDL, BYPASSRLS, tablo sahibi | Flyway migration, DDL |
| `fabric_system` | DML, BYPASSRLS, tablo sahibi DEĞİL | `SystemTransactionExecutor` — onboarding, scheduler, admin |
| `fabric_app` | DML, NOBYPASSRLS | HTTP request-path — JPA/Hibernate |

**Test ortamı notu:** Testcontainers'da tek `test` kullanıcısı (superuser) hem `fabric_app` hem `fabric_system` rolünü üstlenir. `TenantConnectionProvider` RLS parametresini set eder; `SystemTransactionExecutor` BYPASSRLS yolunu kullanır. Prod/test farkı `SystemDataSourceConfig`'te belgelidir.

### Karar 9 — System Chokepoint: `SystemTransactionExecutor` Erişim Kuralları
**Karar tarihi:** 2026-06-06

`SystemTransactionExecutor`, `fabric_system` (BYPASSRLS) rolü ile bağlanan ayrıcalıklı DataSource'u saran tek giriş noktasıdır. RLS'yi bypass ettiği için erişimi **whitelist tabanlı** olarak kısıtlanır.

**Erişim izni verilen sınıflar (whitelist):**
| Sınıf | Gerekçe |
|-------|---------|
| `TenantSystemService` | Cross-tenant yönetim (admin, onboarding, tenant CRUD) |
| `TenantService` | Self-tenant okuma (tenant tablosu self-row RLS'ye tabi) |
| `TenantClonerService` | Onboarding: TEMPLATE→Yeni tenant klon |
| `PlaygroundTTLReaperService` | Scheduled job: süresi dolan playground tenant'ları temizle |
| `TenantQueryAdapter` | Port/Adapter: tenant lookup (auth, event yolu) |
| `CloneTemplateRolesStep` | Onboarding: TEMPLATE rollerini yeni tenant'a kopyala |
| `SystemDataSourceConfig` | Altyapı: DataSource bean konfigürasyonu |

**Kural:** Bu whitelist dışındaki sınıflar `SystemTransactionExecutor` import edemez. Bu kural `ConstitutionArchTest` Rule 14.1 ile build-time'da enforce edilir.

**Yeni sınıf eklemek için:** Whitelist'e eklenmesi gereken her sınıf, bu ADR'ye gerekçesiyle eklenmeli ve ArchUnit testi güncellenmelidir.

### Karar 10 — Onboarding Klon Modeli (Template → BYPASSRLS → Yeni Tenant)
**Karar tarihi:** 2026-06-06

Yeni tenant oluşturulduğunda, TEMPLATE tenant'ından varsayılan veriler (roller, departmanlar, organizasyonlar, kullanıcılar) klonlanır.

**Akış:**
```
TenantOnboardingOrchestrator
  ├── Step 1 (order=1): CreateTenantStep        → TenantSystemService.createTenant() [BYPASSRLS]
  ├── Step 2 (order=2): CreateOrganizationStep   → JPA [fabric_app, yeni tenant context'te]
  ├── Step 3 (order=3): CloneTemplateRolesStep   → SystemTransactionExecutor [BYPASSRLS]
  │                                                 TEMPLATE'ten oku → Yeni tenant'a yaz
  ├── Step 4 (order=4): CreateDepartmentsStep    → JPA [fabric_app]
  ├── Step 5 (order=5): CreateAdminUserStep      → JPA [fabric_app, klonlanan rolleri kullanır]
  └── ...
```

**Kritik sıralama:** `CloneTemplateRolesStep` (order=3) **MUTLAKA** `CreateAdminUserStep` (order=5) öncesinde çalışmalıdır. Admin user'ın atanacağı ADMIN rolü, klonlama ile oluşturulur.

**Atomiklik notu:** `SystemTransactionExecutor` ayrı bir bağlantı/transaction'da çalışır. Sonraki bir step başarısız olursa klonlanan roller geri alınmaz. Orphan veri, tenant'ın soft-delete ile temizlenmesiyle çözülür.

### Karar 11 — DomainEvent Tenant Kontratı
**Karar tarihi:** 2026-06-06

Tüm `DomainEvent` alt sınıfları `tenantId` alanını payload'larında taşımalıdır.

**Gerekçe:**
- `@Async @TransactionalEventListener(AFTER_COMMIT)` listener'lar orijinal thread'in `TenantContext`'ini miras almaz.
- `TenantRestoringEventListenerAspect` event payload'undaki `tenantId`'yi okuyarak listener thread'inde `TenantContext`'i restore eder.
- `tenantId` eksik olan event, listener'da `TenantContext.requireTenantId()` çağrısıyla `IllegalStateException` fırlatır.

**Kural:** `DomainEvent` base class `getTenantId()` abstract metodunu tanımlar. Tüm alt sınıflar bu metodu implemente etmelidir.

---

## Reddedilen Alternatifler
- **DB-per-tenant veya Schema-per-tenant:** 160'dan fazla tablonun her tenant için ayrı schema/DB olarak ayrılması connection pool patlamasına ve DDL migration işlemlerinde büyük karmaşıklığa yol açar. Cross-tenant raporlamayı imkansız hale getirir.
- **Sadece Uygulama Filtresi:** Mevcut durumdur. Koda ve insan dikkatinin disiplinine bağlıdır. Hatalara açıktır.
- **Sentinel Tenant (OR Clause):** Her politikada `OR tenant_id = 'SYSTEM_UUID'` kullanmak performans etkisine yol açar ve uygulamanın `app.current_tenant`'ı kasıtlı/yanlışlıkla bu UUID'ye set etmesine karşı koruma sağlamaz. BYPASSRLS gibi rol tabanlı koruma tercih edilmiştir.
- **PlatformAdminService tam JDBC refactor:** Admin endpoint'leri `TenantContext.executeInTenantContext()` ile hedef tenant'a switch yapıp JPA sorgusu çalıştırıyor. RLS zaten koruma sağladığı ve BYPASSRLS admin'e _tüm_ tenant'ları aynı anda gösterme riski yaratacağı için, mevcut JPA + TenantContext yapısı korunmuştur.

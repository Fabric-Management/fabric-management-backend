# P0 — RLS Tabanlı Tenant İzolasyonu · Review-Edilebilir Görev Planı

> **Amaç:** Tenant izolasyonunu uygulama kodundan alıp veritabanı katmanına taşımak.
> Bir geliştirici (veya AI agent) bir sorguda `tenant_id` filtrelemeyi unutsa bile
> başka firmanın verisi **fiziksel olarak görünmez** olsun.
>
> **Kuzey yıldızı:** Paylaşımlı şema + PostgreSQL Row-Level Security (RLS).
> Uygulama, RLS'i **bypass etmeyen** ayrı bir DB rolüyle bağlanır. Her transaction'da
> bağlantıya `app.current_tenant` set edilir. İzolasyon insana değil, makineye dayanır.

---

## Mevcut durum (kod üzerinden tespit — başlamadan önce oku)

Bu plan teorik değil; aşağıdaki gerçek boşlukları kapatıyor:

1. **RLS pratikte yok.** 169 tablodan yalnızca **3'ünde** politika var
   (`production.inheritance_rule_schema`, `work_order_consumption`, `work_order_output`).
   Kalan ~tenant-scoped tablo (`tenant_id` taşıyan) korumasız.
2. **Tutarsız session değişkeni — bu bir bug.** Mevcut 3 politikadan biri
   `current_setting('app.tenant_id')`, diğer ikisi `current_setting('app.current_tenant')`
   kullanıyor. İkisi aynı anda doğru olamaz.
3. **Bağlantıya değişken set eden kod yok.** Java tarafında `set_config` / `SET LOCAL`
   araması boş döndü. Yani o 3 politika da çalışmıyor; uygulama yetkili (owner) rolle
   bağlandığı için RLS sessizce bypass ediliyor.
4. **Var olan sağlam temel:** `BaseEntity.tenant_id` (+ `@PrePersist` ile otomatik
   yazım), thread-local `TenantContext`, JWT'den context kuran
   `JwtAuthenticationFilter` / `JwtContextInterceptor`, async için
   `TenantAwareTaskDecorator`. Yazım yolu hazır; **okuma yolu izolasyonu eksik.**

Sonuç: yapılacak iş "RLS ekle" değil, **"RLS'i tutarlı, bypass-edilemez ve
makineyle-zorlanır hale getir"**.

---

## Görev sırası ve bağımlılıklar

```
T0 (Karar/ADR)
   └─> T1 (Az-yetkili DB rolü)  ── keystone
          └─> T2 (Bağlantıya tenant binding)
                 └─> T3 (Tüm tablolara RLS politikası)
                        ├─> T4 (Kontrollü system/bypass yolu)
                        ├─> T5 (Testcontainers izolasyon kanıtı)
                        ├─> T6 (CI korkuluğu: RLS-eksik tablo = build fail)
                        └─> T7 (Async/scheduled/outbox kapsaması)
```

Her görev tek başına review edilebilir ve merge edilebilir. **T1 keystone'dur:**
o yapılmadan T3'teki politikalar hiçbir şey yapmaz (owner rol RLS'i bypass eder).

---

## T0 — Karar kaydı (ADR): RLS sözleşmesini kilitle

**Neden:** Sonraki tüm görevler bu kararlara dayanıyor. Yazılı olmazsa agent'lar
farklı varsayımlarla kodlar.

**Kapsam:** `docs/architecture/adr/` altında bir ADR yaz. Kilitlenecek kararlar:

- **Strateji:** Paylaşımlı şema + RLS (DB-per-tenant veya schema-per-tenant *değil*).
- **Tek session değişkeni:** `app.current_tenant` (UUID). `app.tenant_id` tamamen
  terk edilir.
- **Politika şekli:** Her tenant-scoped tabloda `ENABLE` **ve** `FORCE ROW LEVEL SECURITY`;
  `USING` **ve** `WITH CHECK` ikisi de tanımlı (okuma + yazma simetrik korunur).
- **Deny-by-default:** `current_setting('app.current_tenant', true)` NULL ise hiçbir satır
  görünmez (tüm satırlar değil). Politika bunu garanti eden biçimde yazılır.
- **System/cross-tenant yolu:** Onboarding, seeding, admin istatistikleri tenant'sız
  çalışır → T4'te tanımlanan kontrollü ayrıcalıklı rol/sentinel ile.
- **DB rolleri:** `owner` (migration/DDL) ve `app` (runtime, BYPASSRLS yok) ayrımı.

**Kabul kriterleri:**
- ADR merge edildi; yukarıdaki 6 karar açık ve gerekçeli.
- "Reddedilen alternatifler" bölümünde DB-per-tenant ve sadece-uygulama-filtresi neden
  elendiği yazıyor.

**Review checklist:**
- [ ] Session değişken adı tek ve net (`app.current_tenant`).
- [ ] FORCE RLS kararı var (owner dışı app rolü için zorunlu).
- [ ] Deny-by-default davranışı açıkça belirtilmiş.

---

## T1 — Az-yetkili uygulama DB rolü (keystone)

**Neden:** RLS, tablonun sahibi ve superuser için **varsayılan olarak bypass edilir**.
Uygulama şu an muhtemelen owner ile bağlanıyor → tüm politikalar etkisiz. Bu görev
olmadan T3 anlamsız.

**Kapsam:**
- Yeni versiyonlu migration: `app_user` (runtime) rolü oluştur — `NOSUPERUSER`,
  `NOBYPASSRLS`, tablo sahibi **değil**. İlgili şemalarda `USAGE` + tablolarda
  `SELECT/INSERT/UPDATE/DELETE` grant'leri.
- Migration/DDL ayrı bir owner rolüyle çalışmaya devam eder (Flyway).
- `application*.yml` datasource `username`'i `app_user`'a çevrilir
  (lokal/docker/prod profilleri dahil; secret'lar env'den).
- Not: `FORCE ROW LEVEL SECURITY` (T3) owner'ı da politikaya tabi kıldığı için ek
  güvenlik; yine de app rolü owner olmamalı.

**Kabul kriterleri:**
- App, `app_user` ile bağlanıyor; `app_user` hiçbir tabloda owner değil ve BYPASSRLS yok.
- DDL/migration owner ile geçiyor; uygulama başlıyor; `ddl-auto: validate` bozulmuyor.

**Review checklist:**
- [ ] `app_user`'da `BYPASSRLS` yok (`pg_roles` ile doğrulanmış).
- [ ] Grant'ler şema bazında, gelecekteki tablolar için `ALTER DEFAULT PRIVILEGES` ayarlı.
- [ ] Üç profilde de (local/docker/prod) doğru kullanıcı.

---

## T2 — Her transaction'da bağlantıya tenant binding

**Neden:** RLS politikası `current_setting('app.current_tenant')` okur. Bu değişken her
transaction'da, doğru tenant ile, set edilmeli — connection pool reuse nedeniyle de
transaction sonunda sızdırmamalı.

**Kapsam:**
- Transaction başında `SET LOCAL app.current_tenant = '<uuid>'` (veya
  `set_config('app.current_tenant', ?, true)`) çalıştıran bir mekanizma.
  Önerilen: `TransactionSynchronization` / `@Transactional` çevresinde bir aspect veya
  Spring `AbstractDataSource` sarmalı. `SET LOCAL` kullan → transaction bitince otomatik
  sıfırlanır, havuzdaki bağlantıya sızmaz.
- Kaynak: `TenantContext.requireTenantId()`. Context yoksa → T4'teki system yolu
  devreye girmediği sürece **hata fırlat** (sessizce SYSTEM_TENANT'a düşme yok).
- `getCurrentTenantId()`'nin sessizce `SYSTEM_TENANT_ID` döndüren davranışı gözden
  geçirilir; izolasyon yolunda `requireTenantId()` kullanılır.

**Kabul kriterleri:**
- Tenant context'li her DB transaction'ı, `app.current_tenant` set edilmiş bağlantı kullanır.
- Transaction sonunda değişken sızmıyor (SET LOCAL veya explicit reset ile kanıtlı).
- Context yokken izolasyonlu sorgu → açık hata, sessiz fallback yok.

**Review checklist:**
- [ ] `SET LOCAL` kullanılıyor (havuz sızıntısı yok).
- [ ] Read-only ve read-write transaction'lar kapsanıyor.
- [ ] `JwtContextInterceptor` → `TenantContext` → DB binding zinciri uçtan uca çalışıyor.

---

## T3 — Tüm tenant-scoped tablolara RLS politikası

**Neden:** İzolasyonun asıl uygulandığı yer. 166 tabloyu elle yazmak drift ve insan
hatası demek; üretken (programatik) yaklaşım şart.

**Kapsam:**
- Yeni versiyonlu migration. `information_schema`/`pg_catalog` üzerinde `tenant_id`
  kolonu olan tüm tabloları gezen bir `DO $$ ... $$` bloğu ile her tabloya:
  - `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY`,
  - tek standart politika:
    `USING (tenant_id = current_setting('app.current_tenant', true)::uuid)`
    `WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid)`.
- Mevcut 3 tutarsız politikayı (`app.tenant_id` dahil) **drop edip** standardına çevir.
- `tenant_id` taşımayan referans/sistem tabloları (ör. global lookup) bilinçli olarak
  hariç tutulur ve migration yorumunda listelenir.

**Kabul kriterleri:**
- `tenant_id` kolonu olan her tabloda RLS `enabled` + `forced` ve tek tip politika
  (`pg_policies` ile doğrulanmış).
- Eski `app.tenant_id` referansı sıfır.
- Hariç tutulan tablolar listesi migration içinde gerekçeli.

**Review checklist:**
- [ ] `WITH CHECK` var (tenant A, tenant B adına INSERT/UPDATE edemiyor).
- [ ] `FORCE` var (owner bile politikaya tabi).
- [ ] Politika adlandırması tutarlı (ör. `rls_tenant_isolation`).

---

## T4 — Kontrollü system / cross-tenant yolu

**Neden:** Onboarding (tenant daha yokken), seed servisleri ve platform-admin
istatistikleri tek bir tenant'a ait değil. Bunlar RLS'i kırmadan, **kontrollü** şekilde
geçmeli — yoksa geliştiriciler RLS'i tümden kapatma cazibesine kapılır.

**Kapsam:**
- İki seçenekten biri ADR'de seçilir ve uygulanır:
  1. **Ayrı ayrıcalıklı rol** (BYPASSRLS) yalnızca whitelist'lenmiş altyapı servisleri
     için (onboarding, seeding). Normal istek yolu asla bu rolü kullanmaz.
  2. **Sentinel tenant** + politikaya `OR current_setting(...) = '<system-uuid>'` —
     yalnızca açıkça system bağlamında set edilir.
- Etkilenen sınıflar: `TenantOnboardingOrchestrator`, `CreateTenantStep`,
  `TenantSeedService`, `platform/admin` istatistikleri. Bunlar açık bir
  `runAsSystem(...)` sarmalı ile işaretlenir.

**Kabul kriterleri:**
- Onboarding/seeding RLS açıkken çalışıyor.
- System yolu yalnızca whitelist'lenmiş servislerden erişilebilir; normal controller
  yolundan ulaşılamıyor (test ile kanıtlı).

**Review checklist:**
- [ ] System bypass'ı tek ve denetlenebilir bir noktadan geçiyor.
- [ ] İstek (request) yolundan system rolüne erişim yok.

---

## T5 — İzolasyon kanıtı: Testcontainers entegrasyon testleri

**Neden:** RLS, gerçek Postgres olmadan doğrulanamaz (H2 RLS bilmez). Kanıt yoksa
güvenlik iddiası yoktur.

**Kapsam (gerçek Postgres Testcontainer, `app_user` rolüyle bağlanarak):**
- **Okuma izolasyonu:** Tenant A context'inde, tenant B'nin satırını "filtre unutulmuş"
  ham repository çağrısıyla bile okumaya çalış → 0 satır.
- **Yazma izolasyonu (WITH CHECK):** Tenant A, `tenant_id = B` ile INSERT/UPDATE
  deneyince → reddedilir.
- **Deny-by-default:** Context set edilmeden izolasyonlu sorgu → 0 satır (tüm satırlar
  değil) veya açık hata.
- **System yolu:** T4 sarmalı içinde cross-tenant okuma çalışıyor.

**Kabul kriterleri:**
- 4 senaryo da yeşil; testler `app_user` (owner değil) ile bağlanıyor.
- Test, RLS kapatılırsa **kırmızıya döner** (yani gerçekten RLS'i test ediyor).

**Review checklist:**
- [ ] Test owner değil, app rolüyle bağlanıyor (yoksa false-positive).
- [ ] "Filtreyi kasten unut" senaryosu var — asıl güvence bu.

---

## T6 — CI korkuluğu: RLS-eksik tablo = build fail (agent-proof)

**Neden:** Ekip AI agent'lardan oluşuyor. Gelecekte biri yeni `tenant_id`'li tablo
ekleyip RLS yazmayı unutursa, bu sessizce bir sızıntı kapısı olur. Bunu **makine**
yakalamalı.

**Kapsam:**
- Bir entegrasyon testi (Testcontainers): `pg_policies` / `pg_class.relrowsecurity`
  sorgulanır; `tenant_id` kolonu olup RLS'i `enabled+forced` olmayan **her** tablo için
  test fail eder ve tablo adını raporlar.
- Bilinçli istisnalar açık bir allowlist'te (kod içinde, gerekçeli).
- İsteğe bağlı ek: şema-drift kontrolü — "sıfırdan migrate" vs "artımlı migrate" şema
  diff'i CI'da karşılaştırılır.

**Kabul kriterleri:**
- Yeni `tenant_id`'li tablo + RLS yok → CI kırmızı, tablo adı raporlu.
- Allowlist dışı hiçbir tablo korumasız geçemiyor.

**Review checklist:**
- [ ] Test `ConstitutionArchTest` / mimari test ailesiyle aynı yerde, CI'da koşuyor.
- [ ] Allowlist gerekçeli ve kısa.

---

## T7 — Async, scheduled ve outbox yollarının kapsanması

**Neden:** İzolasyon yalnızca HTTP istek thread'inde değil, `@Async` listener'lar,
scheduled job'lar (ör. `InvoiceOverdueJob`) ve ileride outbox publisher'da da geçerli
olmalı. Bu thread'lerde tenant context taşınmazsa ya hata alırlar ya da (daha kötü)
yanlış/sızdıran sorgu yaparlar.

**Kapsam:**
- `TenantAwareTaskDecorator`'ın yalnızca thread-local'i değil, T2'deki DB binding'i de
  tetiklediğini doğrula/genişlet.
- Scheduled job'lar her döngüde hangi tenant(lar) için çalışacağını açıkça belirleyip
  her biri için context kurar (global "tüm tenant" döngüsü T4 system yolundan geçer).
- İleride outbox publisher eklendiğinde aynı kalıbı kullanması için kısa not/şablon.

**Kabul kriterleri:**
- Bir `@Async` listener ve bir scheduled job, doğru tenant izolasyonuyla çalışıyor (test).
- Context taşınmayan arka plan işi → açık hata, sessiz yanlış-sorgu yok.

**Review checklist:**
- [ ] `TenantAwareTaskDecorator` DB binding'i de kapsıyor.
- [ ] Scheduled job'lar tenant'ı explicit kuruyor.

---

## Bitti tanımı (P0 genel "Definition of Done")

- [ ] App, owner olmayan, BYPASSRLS'siz `app_user` ile bağlanıyor (T1).
- [ ] Her transaction `app.current_tenant`'ı `SET LOCAL` ile bağlıyor, sızdırmıyor (T2).
- [ ] `tenant_id`'li tüm tablolarda `FORCE` RLS + `USING`/`WITH CHECK` tek tip politika (T3).
- [ ] Cross-tenant ihtiyaçlar yalnızca kontrollü system yolundan (T4).
- [ ] Testcontainers ile okuma+yazma+deny-by-default izolasyonu kanıtlı (T5).
- [ ] RLS-eksik tablo CI'ı kırıyor (T6).
- [ ] Async/scheduled yolları kapsanmış (T7).

**En kritik tek cümle:** T1 (app rolü) + T3 (FORCE politikalar) + T5 (filtre-unut testi)
üçü birlikte yeşilse izolasyon gerçektir. Biri eksikse, kalanlar güvenlik tiyatrosudur.

# T1 — Az-Yetkili Uygulama DB Rolü (`fabric_app`) + Rol Ayrımı

> **Keystone görev.** T3'teki RLS politikaları, uygulama RLS'i bypass eden bir rolle
> bağlandığı sürece **hiçbir şey yapmaz**. Bu görev, runtime'ı owner olmayan,
> `NOBYPASSRLS` bir rolle bağlar; migration/DDL ise ayrı owner rolüyle çalışır.
>
> **Bağımlılık:** T0 (ADR) merge edilmiş olmalı. Çıktı: T2'nin (SET LOCAL binding)
> üzerine kurulacağı rol ayrımı.

---

## Amaç

İki DB rolü arasında net, makineyle-zorlanan bir ayrım kurmak:

| Rol | Kullanım | Yetkiler |
|-----|----------|----------|
| `fabric_owner` | Flyway migration, DDL, system/bypass yolu (T4) | Tabloların sahibi, `BYPASSRLS`, `NOSUPERUSER` |
| `fabric_app` | Runtime — tüm HTTP istek yolu, JPA | Owner **değil**, `NOBYPASSRLS`, yalnızca DML (SELECT/INSERT/UPDATE/DELETE) |

Sonuç: bir geliştirici/agent sorguda filtre unutsa bile `fabric_app` RLS'e tabi olduğu
için başka tenant'ın verisi görünmez. DDL (CREATE/DROP/ALTER) `fabric_app` için
yetkisiz → şema değişikliği yalnızca migration yolundan.

---

## Mevcut Durum (Kod Araştırması)

| Bulgu | Kanıt |
|-------|-------|
| **Tek rol, hem DDL hem runtime** | [application-local.yml#L4](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/resources/application-local.yml) `fabric_user`; prod `DB_USERNAME`; docker `POSTGRES_USER` |
| **Flyway ayrı kullanıcı kullanmıyor** | Tüm profillerde `spring.flyway` yalnızca `locations/baseline/default-schema` set ediyor; `url/user/password` yok → Flyway ana `spring.datasource`'u (yani `fabric_user`) miras alıyor |
| **Flyway history şeması** | `spring.flyway.default-schema: common_tenant` → `common_tenant.flyway_schema_history` |
| **Bağlantı havuzu** | HikariCP; local max 10, prod max 20 |
| **JPA** | `ddl-auto: validate` (Hibernate şema oluşturmuyor — iyi; tüm şema Flyway'de) |
| **Şemalar** | `common_tenant, common_company, common_user, common_auth, common_communication, common_audit, common_policy, common_ai, common_approval, production, human, finance, sales_ord, logistics, procurement, costing, sales, i18n, notification, flowboard, iwm` |

Çıkarım: Flyway zaten owner-düzeyi yetki gerektiriyor (DDL). Doğru ayrım = **Flyway'i
owner creds'e, ana datasource'u app creds'e** bağlamak. Spring Boot bunu native destekler:
`spring.flyway.url/user/password` set edilince Flyway ayrı bir bağlantı kullanır.

---

## Proposed Changes

### 1. [NEW] Rol provizyonu (VCS dışı, secret hijyeni)

#### `scripts/db/provision-roles.sql`

Rol oluşturma ve **parolalar** Flyway'e (dolayısıyla VCS'e) girmemeli. Roller cluster
düzeyindedir, veritabanı değil — infra/DBA bir kez çalıştırır (psql değişkenleriyle):

```sql
-- Bir kez, superuser (veya managed PG master) tarafından çalıştırılır.
-- Parolalar psql -v ile dışarıdan verilir:  psql -v owner_pw="..." -v app_pw="..."
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_owner') THEN
    CREATE ROLE fabric_owner LOGIN NOSUPERUSER CREATEDB BYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    CREATE ROLE fabric_app   LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS;
  END IF;
END $$;

ALTER ROLE fabric_owner WITH PASSWORD :'owner_pw';
ALTER ROLE fabric_app   WITH PASSWORD :'app_pw';
```

> [!WARNING]
> `BYPASSRLS` atamak **superuser** gerektirir. Managed Postgres'te (RDS/Cloud SQL/Supabase)
> bunu master/`rds_superuser` rolü yapabilir. Hedef hosting bunu kısıtlıyorsa — Open
> Question Q2'ye bak (FORCE RLS ↔ BYPASSRLS bağı).

**Greenfield/local için** — mevcut `fabric_user`'ı yeniden adlandır (ownership otomatik taşınır):

```sql
ALTER ROLE fabric_user RENAME TO fabric_owner;
ALTER ROLE fabric_owner WITH BYPASSRLS;
```

`docker-compose.yml` Postgres init script'i de iki rolü oluşturacak şekilde güncellenir.

---

### 2. [NEW] Flyway migration: grant'ler + default privileges

#### `V{yyyyMMddHHmmss}__grant_fabric_app_privileges.sql`

`fabric_owner` (Flyway) ile çalışır; roller zaten mevcut (adım 1). Grant'ler ve
`ALTER DEFAULT PRIVILEGES` veritabanı/şema-kapsamlıdır → güvenle Flyway'de durur ve
idempotent'tir.

```sql
-- T1: fabric_app runtime yetkileri. Owner: fabric_owner. DDL grant'i YOK.
DO $$
DECLARE
  s text;
  schemas text[] := ARRAY[
    'common_tenant','common_company','common_user','common_auth',
    'common_communication','common_audit','common_policy','common_ai',
    'common_approval','production','human','finance','sales_ord',
    'logistics','procurement','costing','sales','i18n','notification',
    'flowboard','iwm'
  ];
BEGIN
  FOREACH s IN ARRAY schemas LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO fabric_app', s);
    EXECUTE format(
      'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO fabric_app', s);
    EXECUTE format(
      'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO fabric_app', s);
    -- Gelecekte fabric_owner'ın oluşturacağı tablolar/sequence'ler otomatik grant'lensin
    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES FOR ROLE fabric_owner IN SCHEMA %I '
      'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fabric_app', s);
    EXECUTE format(
      'ALTER DEFAULT PRIVILEGES FOR ROLE fabric_owner IN SCHEMA %I '
      'GRANT USAGE, SELECT ON SEQUENCES TO fabric_app', s);
  END LOOP;
END $$;

-- Flyway history tablosuna app erişimi OLMASIN (migration geçmişini app değiştiremez)
REVOKE ALL ON TABLE common_tenant.flyway_schema_history FROM fabric_app;
```

Kritik noktalar:
- **`CREATE ON SCHEMA` verilmez** → `fabric_app` tablo oluşturamaz/düşüremez (DDL kilitli).
- **`ALTER DEFAULT PRIVILEGES FOR ROLE fabric_owner`** zorunlu: default privileges yalnızca
  belirtilen rolün oluşturduğu nesnelere uygulanır. Tabloları Flyway = `fabric_owner`
  oluşturduğu için bu doğru. (Atlanırsa T3 sonrası eklenen her yeni tablo app'e görünmez olur.)
- **`flyway_schema_history` revoke** edilir: `common_tenant` şemasındaki `ON ALL TABLES`
  grant'i onu da kapsardı; app'in migration geçmişine DML'i olmamalı.

---

### 3. [CHANGE] Datasource ayrımı (üç profil)

Ana `spring.datasource` → `fabric_app`. `spring.flyway` → `fabric_owner` (url açıkça
verilince Spring Boot Flyway için ayrı bağlantı kurar; url verilmezse user/password
**yok sayılır** — bu yüzden url zorunlu).

#### [CHANGE] [application-local.yml](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/resources/application-local.yml)

```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:fabric_management}?stringtype=unspecified}
    username: ${POSTGRES_APP_USER:fabric_app}        # was: fabric_user
    password: ${POSTGRES_APP_PASSWORD:app_dev_2026}
  flyway:
    url: ${DATABASE_URL:jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:fabric_management}}
    user: ${POSTGRES_OWNER_USER:fabric_owner}
    password: ${POSTGRES_OWNER_PASSWORD:owner_dev_2026}
    baseline-on-migrate: true
    default-schema: common_tenant
```

#### [CHANGE] [application-prod.yml](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/resources/application-prod.yml)

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_APP_USERNAME}      # was: DB_USERNAME
    password: ${DB_APP_PASSWORD}
  flyway:
    url: ${DATABASE_URL}
    user: ${DB_OWNER_USERNAME}
    password: ${DB_OWNER_PASSWORD}
```

#### [CHANGE] [application-docker.yml](file:///Users/user/Coding/fabric-management/fabric-management-backend/src/main/resources/application-docker.yml)

```yaml
spring:
  datasource:
    username: ${POSTGRES_APP_USER:fabric_app}
    password: ${POSTGRES_APP_PASSWORD}
  flyway:
    url: ${DATABASE_URL_CONTAINER:jdbc:postgresql://${POSTGRES_HOST:postgres}:${POSTGRES_PORT:5432}/${POSTGRES_DB}}
    user: ${POSTGRES_OWNER_USER:fabric_owner}
    password: ${POSTGRES_OWNER_PASSWORD}
```

**Yeni env değişkenleri** (deploy/secret yönetimine eklenecek): `DB_APP_USERNAME`,
`DB_APP_PASSWORD`, `DB_OWNER_USERNAME`, `DB_OWNER_PASSWORD` (+ local/docker karşılıkları).
Eski `DB_USERNAME`/`DB_PASSWORD` artık owner için kullanılır → adı `DB_OWNER_*`'a taşınır.

---

## User Review Required

> [!IMPORTANT]
> **Rol provizyonu nerede yapılsın? (Önerim: VCS dışı bootstrap)**
> - **Seçenek A (önerilen):** `provision-roles.sql` infra/DBA tarafından bir kez; parolalar
>   secret'tan. Grant'ler Flyway'de. → Parola VCS'e girmez, roller cluster-düzeyinde temiz.
> - **Seçenek B:** Rol oluşturmayı da Flyway migration'a koy. → Tek komut kolaylığı ama
>   parola VCS'e sızar veya placeholder gerekir; ayrıca Flyway DB-düzeyi bir araç, rol
>   cluster-düzeyi — kavramsal kirlilik.
> Önerim **A**.

---

## Open Questions

> [!IMPORTANT]
> **Q1 — `default-schema` / search_path:** `fabric_app` entity'ler şema-nitelikli
> (`@Table(schema=...)`) olduğu için `search_path` set etmeye gerek yok. Yine de bazı
> native sorgular niteliksiz olabilir. `ALTER ROLE fabric_app SET search_path = ...`
> ile sabitleyelim mi, yoksa şema-nitelikli kullanımı ArchUnit/grep ile mi garanti edelim?
> Önerim: search_path set **etme**, nitelikli kullanımı zorla (daha açık).

> [!WARNING]
> **Q2 — FORCE RLS ↔ BYPASSRLS bağı (hosting'e bağımlı, kritik):**
> T0 Karar 3 `FORCE ROW LEVEL SECURITY` seçti → tablo sahibi (owner) bile RLS'e tabi olur.
> Bu durumda owner'ın **cross-tenant veri migration'larını** (ör. `backfill_null_currency`
> tipi DML) ve T4 system yolunu çalıştırabilmesi için `fabric_owner`'ın `BYPASSRLS`
> taşıması **şart**. Eğer hedef managed Postgres `BYPASSRLS` atamasına izin vermiyorsa iki
> seçenek:
> - (a) **FORCE'tan vazgeç:** Owner zaten tablo sahibi olduğu için FORCE olmadan RLS'i
>   doğal bypass eder; `fabric_app` owner olmadığından yine tam korunur. Kaybedilen:
>   owner-DML'inin defense-in-depth filtresi.
> - (b) **FORCE'u koru**, owner'a `BYPASSRLS` ver (hosting destekliyorsa).
> **Karar hedef hosting'e bağlı — hangi managed PG / self-hosted?** Bunu netleştirmeden
> T3'ün FORCE satırları kesinleşmemeli.

> [!NOTE]
> **Q3 — Test profili:** Testcontainers testleri (T5) `fabric_app` ile mi bağlanmalı?
> Evet — yoksa RLS testleri owner'la false-positive verir. T1 kapsamında test datasource'u
> da app/owner ayrımını yansıtacak şekilde hazırlanmalı (T5 buna dayanacak).

---

## Verification Plan

### Otomatik / komut

```sql
-- 1. Rol nitelikleri doğru mu?
SELECT rolname, rolsuper, rolbypassrls, rolcreatedb
FROM pg_roles WHERE rolname IN ('fabric_owner','fabric_app');
-- beklenen: fabric_app → rolsuper=f, rolbypassrls=f ; fabric_owner → rolbypassrls=t

-- 2. fabric_app hiçbir tablonun sahibi değil
SELECT tableowner, count(*) FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog','information_schema')
GROUP BY tableowner;   -- fabric_app görünmemeli

-- 3. fabric_app DDL yapamaz (app bağlantısında çalıştır → hata beklenir)
CREATE TABLE production.should_fail (id int);   -- ERROR: permission denied for schema

-- 4. fabric_app DML yapabilir (app bağlantısında → başarı)
SELECT count(*) FROM production.work_order_output;
```

### Entegrasyon

- Uygulama `fabric_app` ile **başlıyor**; `ddl-auto: validate` geçiyor (şema okunabiliyor).
- Flyway `fabric_owner` ile çalışıyor; `flyway_schema_history` yazılıyor; `fabric_app`
  bu tabloya erişemiyor (REVOKE doğrulanır).
- Smoke RLS testi (Testcontainers, `fabric_app`): T0 migration'ıyla politikalı 3 tablodan
  birine tenant A ile satır yaz, context'i B'ye çevir → satır görünmemeli. (Bu, T1'in
  "app gerçekten RLS'e tabi" kanıtı; tam izolasyon paketi T5'te.)

### CI

```bash
./mvnw test -Dtest="ApplicationContextLoadsIT"        # app, fabric_app ile ayağa kalkıyor
./mvnw flyway:info                                     # migration owner ile uygulanıyor
```

---

## Review Düzeltmeleri (AUTHORITATIVE — implementasyon bunları izler)

İmplementasyon planı review edildi. Aşağıdaki dört düzeltme **bağlayıcıdır**; plandaki
ilgili satırların yerine geçer.

### D1 — `public.event_publication` için dar grant (EKLE)
Doğrulandı: `public.event_publication` V001 L58'de tanımlı ve `spring-modulith-events-jpa`
aktif (pom L153) → app runtime'da bu tabloya DML yapıyor. Grant migration'a ekle, ama
**yalnızca bu tabloya**, `public`'in tamamına değil:
```sql
GRANT USAGE ON SCHEMA public TO fabric_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.event_publication TO fabric_app;
-- public'e CREATE veya ON ALL TABLES VERME.
```
`event_publication` RLS'den hariç kalır (T0 Q3); bu yüzden event payload'ı `tenantId`
taşımalı (T7 bağımlılığı).

### D2 — `FOR ROLE fabric_owner`'ı KALDIR (gerçek bug)
Plandaki `ALTER DEFAULT PRIVILEGES FOR ROLE fabric_owner ...` testte patlar: Testcontainers'ta
Flyway, container default kullanıcısı (`test`) ile bağlanır ve `fabric_owner` rolü orada
yoktur → `role "fabric_owner" does not exist`. Var olsa bile tabloları `test` oluşturduğu
için default'lar uygulanmaz. **Düzeltme:** `FOR ROLE` ibaresini tamamen çıkar. Belirtilmezse
default privileges, ALTER'ı çalıştıran **mevcut rolün** (her ortamda Flyway-owner) gelecekte
oluşturacağı nesnelere uygulanır — owner adından bağımsız, doğru davranış:
```sql
ALTER DEFAULT PRIVILEGES IN SCHEMA %I
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fabric_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA %I
  GRANT USAGE, SELECT ON SEQUENCES TO fabric_app;
```

### D3 — Testteki `GRANT ALL ON DATABASE ... TO fabric_app`'i SİL (least-privilege ihlali)
`GRANT ALL ON DATABASE` `CREATE` içerir → app rolü şema oluşturabilir; görevin amacına
aykırı. Yorum ("Flyway needs schema access first") da hatalı — Flyway owner ile çalışır.
Bu satırı kaldır; tablo grant'leri migration'dan gelir, `CONNECT` zaten PUBLIC üzerinden var.

### D4 — `provision-roles.sql` rename'ini koşullu yap
`ALTER ROLE fabric_user RENAME TO fabric_owner` yalnızca `fabric_user` varsa çalışır. Sıfır
ortam için: varsa rename, yoksa `fabric_owner`'ı doğrudan oluştur (idempotent DO bloğu).

### D5 (karar) — Hosting / FORCE↔BYPASSRLS T3'e ERTELENDİ
T1 hosting'den bağımsızdır. Local/docker/test self-hosted Postgres olduğu için
`fabric_owner WITH BYPASSRLS` orada sorunsuz kurulur. Prod owner'ının BYPASSRLS'i ve
FORCE RLS kararı **T3'te** verilir (Plan A: FORCE yok / Plan B: FORCE + BYPASSRLS). T1 kodu
her iki halde de aynıdır. Bu yüzden T1 bu karar olmadan tamamlanır.

---

## Definition of Done

- [ ] `fabric_owner` (BYPASSRLS, local/docker/test) ve `fabric_app` (NOBYPASSRLS, owner değil) rolleri mevcut.
- [ ] Grant migration uygulandı; `fabric_app` 21 şemada DML + sequence yetkili, DDL yetkisiz.
- [ ] **D1:** `public.event_publication` için dar DML grant'i verildi; `public`'e CREATE yok.
- [ ] `flyway_schema_history` app'ten revoke edildi.
- [ ] **D2:** `ALTER DEFAULT PRIVILEGES` `FOR ROLE` olmadan (mevcut role göre) ayarlı.
- [ ] **D3:** Testte `GRANT ALL ON DATABASE` yok; app yetkileri yalnızca migration'dan.
- [ ] Üç profilde datasource=app, flyway=owner; `spring.flyway.url` her profilde açık; yeni env değişkenleri dokümante.
- [ ] App `fabric_app` ile ayağa kalkıyor; Flyway owner ile geçiyor.
- [ ] Smoke RLS testi `fabric_app`'in RLS'e tabi olduğunu kanıtlıyor.
- [ ] **D5:** FORCE↔BYPASSRLS kararı T3'e devredildi (T1'i bloklamaz).

---

## Sonraki Görev

```
T0 ✅ (ADR + session-fix)
   └─> T1 (bu görev) — rol ayrımı
          └─> T2 — SET LOCAL app.current_tenant binding (TransactionSynchronization)
```

T1 merge edilince T2: her transaction başında `fabric_app` bağlantısına
`SET LOCAL app.current_tenant = '<uuid>'` bağlayan mekanizma (boş-string tuzağına dikkat,
T0 review notu).

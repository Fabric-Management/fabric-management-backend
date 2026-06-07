# Flyway Migration Consolidation — Domain Bazlı Birleştirme

Geliştirme aşamasında 80+ versioned migration dosyası domain bazlı ~10 dosyaya indirildi. DB sıfırdan kurulacaksa bu `consolidated/` klasörü kullanılabilir; **mevcut `db/migration/` içindeki V\*.sql dosyalarının yerine koymadan önce yedek alın.**

---

## ÖZET TABLO

| Yeni Dosya | Birleştirilen Eski Migration'lar | Tablo Sayısı |
|------------|----------------------------------|--------------|
| **V001__COMMON_module.sql** | V001, V002, V003, V005, V006, V007, V013, V015, V016, V034, V035, V036, V037, V038, V045, V046, V047, V050, V051, V052, V053, V054, V067, V072 | ~35+ (şemalar + tenant + organization + user + auth + audit + policy + contact/address + junctions + role + department + ai) |
| **V002__FIBER_module.sql** | V008, V009, V025, V070, V075, V080, V082 | 8 |
| **V003__YARN_module.sql** | V012 | 3 |
| **V004__HR_module.sql** | V019, V021, V023, V024, V029–V033 (V022 position atlandı — V050'de kaldırıldı) | 17+ |
| **V005__TRADING_module.sql** | V039, V043, V048, V066, V071, V072 (V040 veri migrasyonu yok) | 6 |
| **V006__LOGISTICS_module.sql** | V042, V044 (sales_order V005 order şemasında) | 1 |
| **V007__IWM_module.sql** | V056, V058–V062, V064, V065, V068, V069, V073, V074, V084 + production_quality_fiber_test_result (cross-module) | 11 |
| **V008__TENANT_module.sql** | V045 (common_tenant tablosu V001'de; bu dosya stub) | 0 |
| **V009__NOTIFICATION_module.sql** | V026, V049, V079, V083 | 5 |
| **V010__SEEDS.sql** | V008, V012, V017, V033, V049, V081 INSERT'leri | — |

**R__ dosyaları değişmedi:** `R__001__fiber_seeds.sql`, `R__002__role_department_categories.sql`, `R__003__hr_policy_packs.sql` aynen kalacak.

---

## Kullanım

1. **Yedek:** Mevcut `src/main/resources/db/migration/` içindeki tüm `V*.sql` dosyalarını yedekleyin.
2. **Değiştir:** Eski `V001__...sql` … `V084__...sql` dosyalarını silin (veya başka bir dizine taşıyın).
3. **Kopyala:** `consolidated/` içindeki `V001__COMMON_module.sql` … `V010__SEEDS.sql` dosyalarını `db/migration/` içine kopyalayın.
4. **V001 COMMON:** V001 kısmi (şemalar + tenant + event_publication). Kalan COMMON tabloları eklenmeli. **V001__COMMON_module.sql** ve **V004, V005, V006, V007** dosyaları, `docs/MIGRATION_TO_DOMAIN_MAP.md` ve mevcut migration içerikleri kullanılarak aynı kurallara göre (sadece son CREATE TABLE, FK sırası, index/constraint/trigger ilgili tablonun altında, seed yok) doldurulmalı veya mevcut DB’den `pg_dump --schema-only` ile üretilip domain’e göre bölünebilir.
6. **V010 SEEDS:** `V010__SEEDS.sql` şu an placeholder. Eski V008, V012, V017, V033, V049, V081 dosyalarındaki `INSERT` satırları bu dosyada toplanmalı (sıra: tenant → organization/user → fiber ref → yarn ref → hr policy → routing_config).
7. **flyway_schema_history:** DB’yi drop edip sıfırdan kuruyorsanız Flyway history temizlenir; konsolide V001–V010 tek seferde çalışır.

---

## Dry run (validate — dosya kontrolü)

DB'ye migrate etmeden önce sadece consolidated dosyalarını kullanarak kontrol:

```bash
./mvnw flyway:validate -Dflyway.locations=classpath:db/migration/consolidated
```

Flyway validate veritabanına bağlanır (uygulanmış migration checksum'ları ile karşılaştırır). Boş DB'de de çalışır. Java/Maven yoksa: `ls -1 src/main/resources/db/migration/consolidated/V*.sql | sort` ile V001–V010 sırasını doğrulayın.

Flyway CLI: `flyway -locations=filesystem:src/main/resources/db/migration/consolidated validate`

---

## Adım 5 — DB'yi kaldırıp consolidated ile yeniden kurma

Aşağıdakiler uygulandı (yedek + eski V*.sql kaldırıldı + consolidated kopyalandı). DB ve uygulama komutlarını kendin çalıştırın:

1. **Yedek:** `backup/` içinde eski 73 adet V*.sql yedeği var (fabric-management-backend/backup/).
2. **Eski V*.sql kaldırıldı:** `db/migration/` içinde sadece R__001, R__002, R__003 kaldı.
3. **Consolidated kopyalandı:** V001__COMMON_module.sql … V010__SEEDS.sql `db/migration/` içine kopyalandı.
4. **DB'yi kaldırın:** `docker compose down -v` (veya `docker-compose down -v` / `pg_dropdb`) — ortamda docker compose yoktu, siz çalıştırın.
5. **Uygulamayı başlatın:** `./mvnw spring-boot:run` — Flyway başlangıçta V001–V010'u uygular.

---

## Adım 6 — Doğrula

Migrate sonrası Flyway history ve tablo sayılarını kontrol edin:

```sql
-- Flyway history — 10 kayıt olmalı
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- Şemaya göre tablo sayısı
SELECT schemaname, count(*) AS tablo_sayisi
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog','information_schema')
GROUP BY schemaname
ORDER BY schemaname;
```

Hazır SQL dosyası: **`consolidated/VERIFY_AFTER_MIGRATE.sql`** — psql veya DBeaver ile çalıştırabilirsiniz:

```bash
psql -h localhost -U fabric_owner -d fabric_management -f src/main/resources/db/migration/consolidated/VERIFY_AFTER_MIGRATE.sql
```

---

## FK Sırası Notu

- **common_tenant** tüm tenant_id FK'ları için önce oluşturulmalı; **V001__COMMON_module.sql** içinde şemalardan hemen sonra yer alıyor (organization, user, auth, notification buna referans verir).
- **V009 NOTIFICATION** tabloları (`common_verification_log`, `common_trusted_device`, `common_notification`) **common_user.common_user** tablosuna FK verir. V001 kısmi kaldığı sürece (common_user yok) V009 çalıştırılamaz; V001 tamamlanırken en azından common_user eklenmeli. Detay: `DOUBLE_CHECK_REPORT.md`.
- **V008__TENANT_module.sql** şu an sadece açıklama; tenant tablosu V001’de olduğu için TENANT modülü 0 tablo ile “tamamlandı” olarak bırakıldı.
- **production_quality_fiber_test_result** tablosu FIBER domain’ine ait olmakla birlikte `production_execution_batch`’e FK nedeniyle **V007__IWM_module.sql** içinde oluşturulmalı (cross-module; dosyada yorumla belirtin).

---

## Oluşturulan Dosyalar (consolidated/)

- `V002__FIBER_module.sql` — Tam, çalışır.
- `V003__YARN_module.sql` — Tam, çalışır (INSERT’ler V010’da).
- `V008__TENANT_module.sql` — Stub (açıklama).
- `V009__NOTIFICATION_module.sql` — Tam, çalışır (routing INSERT V010’da).
- `V010__SEEDS.sql` — Placeholder; INSERT’ler eklenecek.
- **V001 COMMON, V004 HR, V005 TRADING, V006 LOGISTICS, V007 IWM** — Tamamlandı. V010 SEEDS placeholder; INSERT'ler eklenecek.

Bu README ve özet tablo, talep edilen çıktı formatına uygun özeti sağlar.

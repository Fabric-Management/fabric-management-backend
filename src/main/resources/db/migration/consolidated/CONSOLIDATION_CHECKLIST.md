# Consolidated Migration — Özet Kontrol Listesi

## Yapılanlar (oturumda tamamlandı)

- [x] **Adım 1:** V001 COMMON tamamlandı (şemalar, tenant, organization, user, auth, contact/address, junctions, role, department, audit, policy, ai)
- [x] **Adım 2:** V004 HR yazıldı (V019, V021, V023, V024, V029–V033; V022 atlandı)
- [x] **Adım 2:** V005 TRADING yazıldı (V039, V043, V048, V066, V071, V072)
- [x] **Adım 2:** V006 LOGISTICS yazıldı (V042, V044)
- [x] **Adım 2:** V007 IWM yazıldı (V056, V058–V062, V064, V065, V068, V069, V073, V074, V084 + production_quality_fiber_test_result)
- [x] **Adım 3:** V010 SEEDS dolduruldu (tenant → org/user → fiber ref → yarn ref → hr policy → routing_config)
- [x] **Adım 5 (kısmi):** Eski V\*.sql yedeklendi → `backup/`; kaldırıldı; consolidated V001–V010 `db/migration/` içine kopyalandı. R__ dosyalarına dokunulmadı.

## Sizin yapmanız gerekenler

- [ ] **Adım 4:** Flyway validate — hata yok  
  `./mvnw flyway:validate -Dflyway.locations=classpath:db/migration/consolidated`

- [ ] **Adım 5 (devam):** DB kaldırıldı ve yeniden kuruldu  
  `docker compose down -v` (veya `docker-compose down -v` / `pg_dropdb`)

- [ ] **Adım 5 (devam):** Uygulamayı ayağa kaldırın  
  `./mvnw spring-boot:run`

- [ ] **Adım 6:** flyway_schema_history 10 kayıt  
  `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;`

- [ ] **Adım 6:** Tüm şemalar ve tablolar var  
  `SELECT schemaname, count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') GROUP BY schemaname ORDER BY schemaname;`  
  Hazır dosya: `consolidated/VERIFY_AFTER_MIGRATE.sql`

- [ ] **Uygulama başarıyla ayağa kalktı**  
  Spring Boot log’unda Flyway V1–V10 başarılı, sunucu port’ta ayakta.

---

**Özet:** 1–3 ve 5 (yedekleme/kopyalama) tamamlandı. 4, 5 (DB + run) ve 6 sizin ortamınızda çalıştırılıp işaretlenebilir.

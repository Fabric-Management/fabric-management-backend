-- ============================================
-- Adım 6 — Doğrulama (consolidated migrate sonrası)
-- Çalıştır: psql -h localhost -U fabric_owner -d fabric_management -f VERIFY_AFTER_MIGRATE.sql
-- veya IDE / DBeaver ile aşağıdaki SELECT'leri çalıştır (\echo sadece psql'de çalışır).
-- ============================================

-- 1. Flyway history — 10 kayıt beklenir (V1 .. V10)
SELECT installed_rank,
       version,
       description,
       success,
       installed_on
FROM flyway_schema_history
ORDER BY installed_rank;

-- 2. Şemaya göre tablo sayısı
SELECT schemaname,
       count(*) AS tablo_sayisi
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
GROUP BY schemaname
ORDER BY schemaname;

-- 3. Toplam tablo sayısı
SELECT count(*) AS toplam_tablo
FROM pg_tables
WHERE schemaname NOT IN ('pg_catalog', 'information_schema');

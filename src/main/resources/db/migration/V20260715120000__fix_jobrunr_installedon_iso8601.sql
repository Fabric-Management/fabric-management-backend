-- JobRunr, jobrunr_migrations.installedon kolonunu Instant.parse() ile okur ve ISO-8601 bekler
-- (2026-07-15T08:07:36.664757Z). V20260603083000 seed'i NOW()::varchar(29) kullandı; bu Postgres
-- formatı üretir (2026-07-15 08:07:36.664757+00) ve DatabaseCreator$MigrationsTableLocker
-- .isMigrationsTableLocked() içinde DateTimeParseException'a yol açar -> StorageProvider bean'i
-- oluşamaz -> tüm Spring context'leri düşer.
--
-- Eski dosya checksum nedeniyle düzenlenemez; bozuk satırları burada normalize ediyoruz.
-- Yeni DB'lerde V20260603083000 önce bozuk yazar, bu migration hemen ardından düzeltir.
--
-- NOT: org.jobrunr.database.skip-create=true JobRunr 7.1.1 Spring starter'ında BAĞLI DEĞİL —
-- JobRunrSqlStorageAutoConfiguration, SqlStorageProviderFactory.using(dataSource, tablePrefix)
-- çağırır ve bu overload DatabaseOptions.CREATE'i sabitler. Yani runMigrations() her başlangıçta
-- çalışır. Bu satırların doğru formatta olması bu yüzden şart. Ayrıntı: BUG-jobrunr-skip-create.

UPDATE public.jobrunr_migrations
SET installedon = to_char(
      COALESCE(
        NULLIF(installedon, '')::timestamptz AT TIME ZONE 'UTC',
        now() AT TIME ZONE 'UTC'
      ),
      'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'
    )
WHERE installedon NOT LIKE '%T%';

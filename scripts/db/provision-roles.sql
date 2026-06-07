-- Bir kez, superuser (veya managed PG master) tarafından çalıştırılır.
-- Parolalar psql -v ile dışarıdan verilir:  psql -v owner_pw="..." -v app_pw="..."
-- Local geliştirme için bu betik doğrudan db-reset-local.sql içinden de çağrılabilir veya kodu oraya eklenebilir.

DO $$
BEGIN
  -- Greenfield: fabric_user varsa rename et
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_user') THEN
    ALTER ROLE fabric_user RENAME TO fabric_owner;
    ALTER ROLE fabric_owner WITH BYPASSRLS;
  -- Sıfır ortam: fabric_owner yoksa oluştur
  ELSIF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_owner') THEN
    CREATE ROLE fabric_owner LOGIN NOSUPERUSER CREATEDB BYPASSRLS;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN
    CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS;
  END IF;

  -- fabric_system: runtime system operations (BYPASSRLS, no DDL)
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
    CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS;
  END IF;
END $$;

-- Parolaları ayarla (Eğer ortam değişkeni ile aktarılmışsa çalışır, aksi halde \set ile ayarlanmalı)
ALTER ROLE fabric_owner  WITH PASSWORD :'owner_pw';
ALTER ROLE fabric_app    WITH PASSWORD :'app_pw';
ALTER ROLE fabric_system WITH PASSWORD :'system_pw';

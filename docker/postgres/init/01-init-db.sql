-- Kullanıcı oluştur
CREATE USER fabric_user WITH LOGIN PASSWORD 'fabric_pass_2024';

-- Veritabanı yetkileri
GRANT ALL PRIVILEGES ON DATABASE fabric_management_db TO fabric_user;

-- Schema'ları oluştur
CREATE SCHEMA IF NOT EXISTS keycloak;
CREATE SCHEMA IF NOT EXISTS user_service;
CREATE SCHEMA IF NOT EXISTS contact_service;
CREATE SCHEMA IF NOT EXISTS auth_service;
CREATE SCHEMA IF NOT EXISTS company_service;
CREATE SCHEMA IF NOT EXISTS employee_service;
CREATE SCHEMA IF NOT EXISTS inventory_service;
CREATE SCHEMA IF NOT EXISTS order_service;
CREATE SCHEMA IF NOT EXISTS production_service;
CREATE SCHEMA IF NOT EXISTS accounting_service;
CREATE SCHEMA IF NOT EXISTS logistics_service;

-- Schema yetkileri
GRANT ALL ON SCHEMA public TO fabric_user;
GRANT ALL ON SCHEMA keycloak TO fabric_user;
GRANT ALL ON SCHEMA user_service TO fabric_user;
GRANT ALL ON SCHEMA contact_service TO fabric_user;
GRANT ALL ON SCHEMA auth_service TO fabric_user;
GRANT ALL ON SCHEMA company_service TO fabric_user;
GRANT ALL ON SCHEMA employee_service TO fabric_user;
GRANT ALL ON SCHEMA inventory_service TO fabric_user;
GRANT ALL ON SCHEMA order_service TO fabric_user;
GRANT ALL ON SCHEMA production_service TO fabric_user;
GRANT ALL ON SCHEMA accounting_service TO fabric_user;
GRANT ALL ON SCHEMA logistics_service TO fabric_user;

-- Gelecekte oluşturulacak tablolar için default yetkiler
ALTER DEFAULT PRIVILEGES IN SCHEMA user_service GRANT ALL ON TABLES TO fabric_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA contact_service GRANT ALL ON TABLES TO fabric_user;
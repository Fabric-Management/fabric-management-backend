-- ========================================
-- Fabric Management System Database Setup
-- ========================================

-- Create main database first
CREATE DATABASE fabric_management;

-- Auth Service Database
CREATE USER auth_service WITH PASSWORD 'auth_service_password';
CREATE DATABASE auth_db OWNER auth_service;
GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_service;

-- User Service Database
CREATE USER user_service WITH PASSWORD 'user_service_password';
CREATE DATABASE user_db OWNER user_service;
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_service;

-- Contact Service Database
CREATE USER contact_service WITH PASSWORD 'contact_service_password';
CREATE DATABASE contact_db OWNER contact_service;
GRANT ALL PRIVILEGES ON DATABASE contact_db TO contact_service;

-- Company Service Database
CREATE USER company_service WITH PASSWORD 'company_service_password';
CREATE DATABASE company_db OWNER company_service;
GRANT ALL PRIVILEGES ON DATABASE company_db TO company_service;

-- Grant schema permissions for each service in their respective databases
\c auth_db;
GRANT ALL ON SCHEMA public TO auth_service;

\c user_db;
GRANT ALL ON SCHEMA public TO user_service;

\c contact_db;
GRANT ALL ON SCHEMA public TO contact_service;

\c company_db;
GRANT ALL ON SCHEMA public TO company_service;

-- Return to main database
\c fabric_management;

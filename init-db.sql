-- ========================================
-- Fabric Management System Database Setup
-- ========================================

-- Create main database first
CREATE DATABASE fabric_management;

-- User Service Database
CREATE USER user_service WITH PASSWORD 'password';
CREATE DATABASE user_db OWNER user_service;
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_service;

-- Contact Service Database
CREATE USER contact_service WITH PASSWORD 'password';
CREATE DATABASE contact_db OWNER contact_service;
GRANT ALL PRIVILEGES ON DATABASE contact_db TO contact_service;

-- Company Service Database (Future Ready)
CREATE USER company_service WITH PASSWORD 'password';
CREATE DATABASE company_db OWNER company_service;
GRANT ALL PRIVILEGES ON DATABASE company_db TO company_service;

-- Auth Service Database (Future Ready)
CREATE USER auth_service WITH PASSWORD 'password';
CREATE DATABASE auth_db OWNER auth_service;
GRANT ALL PRIVILEGES ON DATABASE auth_db TO auth_service;

-- Admin user for management
CREATE USER fabric_admin WITH PASSWORD 'FabricAdmin2025!';
ALTER USER fabric_admin CREATEDB;
GRANT ALL PRIVILEGES ON DATABASE fabric_management TO fabric_admin;

-- Add extensions if needed
\c user_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c contact_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create schema for contact_service
CREATE SCHEMA IF NOT EXISTS contact_service AUTHORIZATION contact_service;
GRANT ALL ON SCHEMA contact_service TO contact_service;

\c company_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c auth_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
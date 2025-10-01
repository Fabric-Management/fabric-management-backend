-- ========================================
-- FABRIC MANAGEMENT SYSTEM - DATABASE INIT
-- ========================================
-- Minimal initialization - Tables created by Flyway migrations

-- =============================================================================
-- EXTENSIONS
-- =============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- GRANT PRIVILEGES
-- =============================================================================
GRANT ALL ON SCHEMA public TO fabric_user;
GRANT ALL PRIVILEGES ON DATABASE fabric_management TO fabric_user;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL ON TABLES TO fabric_user;
    
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL ON SEQUENCES TO fabric_user;
    
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL ON FUNCTIONS TO fabric_user;

-- =============================================================================
-- SCHEMAS (Optional - for logical separation)
-- =============================================================================
-- Note: We use public schema, but these are prepared for future use
CREATE SCHEMA IF NOT EXISTS user_service;
CREATE SCHEMA IF NOT EXISTS contact_service;
CREATE SCHEMA IF NOT EXISTS company_service;

GRANT ALL ON SCHEMA user_service TO fabric_user;
GRANT ALL ON SCHEMA contact_service TO fabric_user;
GRANT ALL ON SCHEMA company_service TO fabric_user;

-- Set search path
ALTER DATABASE fabric_management SET search_path TO public, user_service, contact_service, company_service;

-- =============================================================================
-- FLYWAY SCHEMA HISTORY TABLE
-- =============================================================================
-- Ensure Flyway can manage its schema history
GRANT ALL ON ALL TABLES IN SCHEMA public TO fabric_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO fabric_user;

-- =============================================================================
-- DATABASE CONFIGURATION
-- =============================================================================
-- Performance tuning
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET checkpoint_completion_target = '0.9';
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET default_statistics_target = '100';
ALTER SYSTEM SET random_page_cost = '1.1';
ALTER SYSTEM SET effective_io_concurrency = '200';

-- Connection settings
ALTER SYSTEM SET max_connections = '200';

-- Logging
ALTER SYSTEM SET log_min_duration_statement = '1000'; -- Log queries longer than 1 second

-- =============================================================================
-- NOTES
-- =============================================================================
-- Tables are created by Flyway migrations in each service:
--   - User Service: V1__create_user_tables.sql
--   - Contact Service: V1__create_contact_tables.sql
--   - Company Service: V1__create_company_tables.sql
--
-- This init script only prepares the database environment.
-- =============================================================================

-- ========================================
-- FABRIC MANAGEMENT SYSTEM - DATABASE INIT
-- ========================================
-- Clean and optimized database initialization
-- ========================================

-- =============================================================================
-- 1. EXTENSIONS
-- =============================================================================
-- Enable required PostgreSQL extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";     -- For UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";      -- For encryption functions
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements"; -- For query performance monitoring

-- =============================================================================
-- 2. DATABASE CONFIGURATION
-- =============================================================================
-- Performance tuning for development environment
-- Note: These settings should be adjusted for production

-- Memory settings (adjust based on available RAM)
ALTER SYSTEM SET shared_buffers = '256MB';
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET maintenance_work_mem = '64MB';
ALTER SYSTEM SET work_mem = '4MB';

-- Checkpoint settings
ALTER SYSTEM SET checkpoint_completion_target = '0.9';
ALTER SYSTEM SET wal_buffers = '16MB';
ALTER SYSTEM SET min_wal_size = '1GB';
ALTER SYSTEM SET max_wal_size = '2GB';

-- Query optimization
ALTER SYSTEM SET default_statistics_target = '100';
ALTER SYSTEM SET random_page_cost = '1.1';
ALTER SYSTEM SET effective_io_concurrency = '200';
ALTER SYSTEM SET parallel_workers_per_gather = '2';

-- Connection settings
ALTER SYSTEM SET max_connections = '100';  -- Reduced from 200
ALTER SYSTEM SET idle_in_transaction_session_timeout = '10min';

-- Logging (for development)
ALTER SYSTEM SET log_statement = 'ddl';
ALTER SYSTEM SET log_min_duration_statement = '500'; -- Log slow queries (>500ms)
ALTER SYSTEM SET log_checkpoints = 'on';
ALTER SYSTEM SET log_connections = 'on';
ALTER SYSTEM SET log_disconnections = 'on';
ALTER SYSTEM SET log_lock_waits = 'on';
ALTER SYSTEM SET log_temp_files = '0';

-- Apply configuration changes
SELECT pg_reload_conf();

-- =============================================================================
-- 3. ROLES AND PERMISSIONS
-- =============================================================================
-- Create application user if not exists
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_user WHERE usename = 'fabric_user') THEN
        CREATE USER fabric_user WITH PASSWORD 'fabric_password';
    END IF;
END
$$;

-- Grant privileges
GRANT CONNECT ON DATABASE fabric_management TO fabric_user;
GRANT USAGE ON SCHEMA public TO fabric_user;
GRANT CREATE ON SCHEMA public TO fabric_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO fabric_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO fabric_user;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO fabric_user;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL ON TABLES TO fabric_user;
    
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL ON SEQUENCES TO fabric_user;
    
ALTER DEFAULT PRIVILEGES IN SCHEMA public 
    GRANT ALL ON FUNCTIONS TO fabric_user;

-- =============================================================================
-- 4. COMMON FUNCTIONS
-- =============================================================================
-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function for soft delete
CREATE OR REPLACE FUNCTION soft_delete()
RETURNS TRIGGER AS $$
BEGIN
    NEW.deleted = TRUE;
    NEW.deleted_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to check tenant isolation
CREATE OR REPLACE FUNCTION check_tenant_isolation()
RETURNS TRIGGER AS $$
BEGIN
    -- This is a placeholder for tenant isolation logic
    -- Can be enhanced based on multi-tenancy requirements
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 5. MAINTENANCE SETTINGS
-- =============================================================================
-- Auto-vacuum settings for better performance
ALTER SYSTEM SET autovacuum = 'on';
ALTER SYSTEM SET autovacuum_max_workers = '4';
ALTER SYSTEM SET autovacuum_naptime = '30s';
ALTER SYSTEM SET autovacuum_vacuum_threshold = '50';
ALTER SYSTEM SET autovacuum_vacuum_scale_factor = '0.1';
ALTER SYSTEM SET autovacuum_analyze_threshold = '50';
ALTER SYSTEM SET autovacuum_analyze_scale_factor = '0.05';

-- =============================================================================
-- 6. NOTES
-- =============================================================================
-- Tables are created by Flyway migrations in each service:
--   • User Service: V1__create_user_tables.sql
--   • Contact Service: V1__create_contact_tables.sql  
--   • Company Service: V1__create_company_tables.sql
--
-- This script only prepares the database environment.
-- Run after PostgreSQL container starts, before service deployment.
-- =============================================================================

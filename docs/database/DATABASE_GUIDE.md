# Database Guide

## Overview

Fabric Management System uses PostgreSQL as its primary database with a microservice-oriented architecture. Each service maintains its own schema and tables, following the Database-per-Service pattern.

## Database Structure

### Core Principles

- **Database per Service**: Each microservice has its own logical database schema
- **Event Sourcing Ready**: Event tables for audit and replay capabilities
- **Soft Delete**: All entities support soft deletion with `deleted` flag
- **Optimistic Locking**: Version fields prevent concurrent update conflicts
- **Audit Fields**: Automatic tracking of created/updated timestamps and users

### Service Schemas

#### User Service

- **users**: Core user information and authentication
- **user_sessions**: Active session management
- **password_reset_tokens**: Password recovery tokens
- **user_events**: User domain events for audit

#### Contact Service

- **contacts**: Multi-type contact information (email, phone, address)
- Supports multiple owner types (USER, COMPANY)
- Verification workflow for email/phone

#### Company Service

- **companies**: Company profiles and settings
- **company_users**: User-company relationships
- **company_settings**: Extended configuration
- **company_events**: Company domain events

## Database Initialization

### 1. Initial Setup

The database is initialized via `scripts/init-db.sql` which:

- Creates required PostgreSQL extensions
- Configures performance settings
- Sets up common functions
- Establishes user permissions

### 2. Migration Strategy

Each service uses Flyway for database migrations:

```
services/
├── user-service/src/main/resources/db/migration/
│   └── V1__create_user_tables.sql
├── contact-service/src/main/resources/db/migration/
│   └── V1__create_contact_tables.sql
└── company-service/src/main/resources/db/migration/
    └── V1__create_company_tables.sql
```

### 3. Running Migrations

```bash
# Run all migrations
./scripts/run-migrations.sh

# Run specific service migration
./scripts/run-migrations.sh user-service
```

## Common Patterns

### 1. Base Entity Fields

All tables include standard audit fields:

```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
created_by VARCHAR(100),
updated_by VARCHAR(100),
version BIGINT DEFAULT 0,
deleted BOOLEAN NOT NULL DEFAULT FALSE
```

### 2. Automatic Timestamp Updates

Triggers automatically update `updated_at` on modifications:

```sql
CREATE TRIGGER trg_set_updated_at_[table]
  BEFORE UPDATE ON [table]
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();
```

### 3. Indexing Strategy

- Primary keys on UUIDs
- Foreign key relationships indexed
- Frequently queried fields indexed
- Composite indexes for common query patterns

### 4. JSONB for Flexible Data

Settings and preferences stored as JSONB:

- Allows schema flexibility
- Supports complex nested structures
- Queryable with PostgreSQL JSON operators

## Performance Optimization

### Connection Pooling

- HikariCP for connection management
- Max connections: 100 (configurable)
- Idle timeout: 10 minutes

### Query Optimization

- Statement timeout: 30 seconds
- Log slow queries: > 500ms
- Use EXPLAIN ANALYZE for query tuning

### Maintenance

- Auto-vacuum enabled
- Regular ANALYZE for statistics
- Periodic REINDEX for performance

## Security

### Access Control

- Dedicated application user (`fabric_user`)
- Limited permissions per service
- No direct table access from application

### Data Protection

- Sensitive data encrypted at rest
- SSL/TLS for connections
- Password hashing with BCrypt

### Audit Trail

- All changes tracked with timestamps
- User identification in audit fields
- Event sourcing for critical operations

## Backup and Recovery

### Backup Strategy

```bash
# Daily backups
pg_dump -h localhost -U fabric_user -d fabric_management > backup.sql

# Compressed backup
pg_dump -h localhost -U fabric_user -d fabric_management | gzip > backup.sql.gz
```

### Recovery Process

```bash
# Restore from backup
psql -h localhost -U fabric_user -d fabric_management < backup.sql

# Restore compressed backup
gunzip -c backup.sql.gz | psql -h localhost -U fabric_user -d fabric_management
```

## Monitoring

### Key Metrics

- Connection count
- Query performance
- Table sizes
- Index usage
- Cache hit rates

### Useful Queries

```sql
-- Active connections
SELECT count(*) FROM pg_stat_activity;

-- Slow queries
SELECT * FROM pg_stat_statements
ORDER BY total_time DESC LIMIT 10;

-- Table sizes
SELECT schemaname, tablename,
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename))
FROM pg_tables ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Index usage
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes ORDER BY idx_scan;
```

## Troubleshooting

### Common Issues

1. **Connection Refused**

   - Check PostgreSQL is running
   - Verify port configuration (default: 5433)
   - Check firewall rules

2. **Permission Denied**

   - Ensure fabric_user has correct privileges
   - Run init-db.sql to reset permissions

3. **Migration Failed**

   - Check previous migration status
   - Verify database connectivity
   - Review migration SQL for errors

4. **Performance Issues**
   - Analyze slow queries
   - Check index usage
   - Review connection pool settings
   - Consider vacuum/analyze

## Best Practices

1. **Always use migrations** for schema changes
2. **Never modify** production data directly
3. **Test migrations** in development first
4. **Monitor performance** regularly
5. **Backup before** major changes
6. **Use transactions** for data consistency
7. **Index foreign keys** for join performance
8. **Avoid N+1 queries** in application code
9. **Use connection pooling** efficiently
10. **Regular maintenance** (vacuum, analyze, reindex)

---

**Last Updated:** 2025-10-09 20:15 UTC+1  
**Version:** 1.0.0  
**Status:** ✅ Active

# 🗄️ Database Documentation

**Last Updated:** October 10, 2025  
**Purpose:** Database operations, performance, and monitoring  
**Status:** ✅ Active

---

## 📚 Documentation Index

| Document                                 | Description                                                                | When to Use                       |
| ---------------------------------------- | -------------------------------------------------------------------------- | --------------------------------- |
| [DATABASE_GUIDE.md](./DATABASE_GUIDE.md) | ⭐ **Operations guide** - Performance, monitoring, backup, troubleshooting | Database operations & maintenance |

---

## 🎯 Quick Navigation

### By Topic

| Topic                     | Document                                                                                     | Section                      |
| ------------------------- | -------------------------------------------------------------------------------------------- | ---------------------------- |
| **Database Architecture** | [../ARCHITECTURE.md](../ARCHITECTURE.md)                                                     | Database-per-service pattern |
| **Migration Strategy**    | [../deployment/DATABASE_MIGRATION_STRATEGY.md](../deployment/DATABASE_MIGRATION_STRATEGY.md) | Flyway migrations            |
| **Performance Tuning**    | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md)                                                     | Performance Optimization     |
| **Monitoring Queries**    | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md)                                                     | Monitoring                   |
| **Backup/Restore**        | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md)                                                     | Backup and Recovery          |
| **Common Patterns**       | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md)                                                     | Common Patterns              |

### By Task

| Task                 | Guide                                                                                        |
| -------------------- | -------------------------------------------------------------------------------------------- |
| Run migrations       | [../deployment/DATABASE_MIGRATION_STRATEGY.md](../deployment/DATABASE_MIGRATION_STRATEGY.md) |
| Optimize performance | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md#performance-optimization)                            |
| Create backup        | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md#backup-and-recovery)                                 |
| Monitor database     | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md#monitoring)                                          |
| Troubleshoot issues  | [DATABASE_GUIDE.md](./DATABASE_GUIDE.md#troubleshooting)                                     |

---

## 🏗️ Database Architecture

### Service Databases

| Service             | Database Schema | Tables                                      |
| ------------------- | --------------- | ------------------------------------------- |
| **User Service**    | user_db         | users, user_sessions, password_reset_tokens |
| **Contact Service** | contact_db      | contacts, outbox_events                     |
| **Company Service** | company_db      | companies, departments, outbox_events       |

**📖 Complete architecture:** [../ARCHITECTURE.md](../ARCHITECTURE.md)

### Infrastructure

- **Database**: PostgreSQL 15
- **Port**: 5433 (external), 5432 (internal)
- **Connection Pooling**: HikariCP
- **Migrations**: Flyway per service

---

## ⚡ Quick Commands

### Health Check

```bash
# PostgreSQL ready?
docker exec fabric-postgres pg_isready

# Connection test
docker exec fabric-postgres psql -U fabric_user -d fabric_management -c "SELECT 1"
```

### Backup

```bash
# Quick backup
docker exec fabric-postgres pg_dump -U fabric_user fabric_management > backup.sql

# Compressed backup
docker exec fabric-postgres pg_dump -U fabric_user fabric_management | gzip > backup.sql.gz
```

### Monitoring

```bash
# Active connections
docker exec fabric-postgres psql -U fabric_user -d fabric_management \
  -c "SELECT count(*) FROM pg_stat_activity;"

# Database size
docker exec fabric-postgres psql -U fabric_user -d fabric_management \
  -c "SELECT pg_size_pretty(pg_database_size('fabric_management'));"
```

**📖 Complete commands:** [DATABASE_GUIDE.md](./DATABASE_GUIDE.md)

---

## 🔗 Related Documentation

- [ARCHITECTURE.md](../ARCHITECTURE.md) - System & database architecture
- [DATABASE_MIGRATION_STRATEGY.md](../deployment/DATABASE_MIGRATION_STRATEGY.md) - Migration guide
- [DEPLOYMENT_GUIDE.md](../deployment/DEPLOYMENT_GUIDE.md) - Deployment procedures
- [Troubleshooting](../troubleshooting/README.md) - Common issues

---

**Maintained By:** Backend Team  
**Last Updated:** 2025-10-10  
**Status:** ✅ Active

# Migration-Entity Consistency Checklist

**Purpose:** Prevent schema mismatches between migrations and entities.

---

## ✅ Before Committing Migration

### 1. Table Definition
- [ ] `CREATE TABLE` matches entity `@Table(name=..., schema=...)`
- [ ] Table name is consistent (case-sensitive)
- [ ] Schema name is consistent (`common_company`, `common_user`, `production`, etc.)

### 2. Column Mapping
- [ ] All `NOT NULL` columns in migration exist in entity with `nullable = false`
- [ ] All entity `@Column` fields exist in migration
- [ ] Column names match exactly (case-sensitive)
- [ ] Data types are compatible (UUID, VARCHAR, BOOLEAN, TIMESTAMP, etc.)

### 3. Constraints
- [ ] Foreign keys defined in migration match entity `@ManyToOne` / `@OneToMany`
- [ ] Unique constraints match entity `@UniqueConstraint` or `@Index(unique=true)`
- [ ] Check constraints are documented in entity comments

### 4. Indexes
- [ ] Migration indexes match entity `@Index` annotations
- [ ] Composite indexes are correctly defined
- [ ] Partial indexes (WHERE clauses) are documented

### 5. Relationships
- [ ] Junction tables exist for `@ManyToMany` relationships
- [ ] Foreign key columns match `@JoinColumn(name=...)`
- [ ] Cascade operations are consistent

---

## ✅ Before Committing Entity

### 1. Table Mapping
- [ ] `@Table` annotation matches migration `CREATE TABLE`
- [ ] Schema name matches migration schema
- [ ] Table name matches migration table name

### 2. Column Mapping
- [ ] All `@Column` fields exist in migration
- [ ] `nullable = false` matches migration `NOT NULL`
- [ ] `length` constraints match migration `VARCHAR(n)`
- [ ] Default values are consistent

### 3. Relationships
- [ ] `@ManyToOne` / `@OneToMany` foreign keys exist in migration
- [ ] `@JoinColumn(name=...)` matches migration column name
- [ ] Junction tables exist for `@ManyToMany`

### 4. Base Entity Fields
- [ ] `id`, `tenant_id`, `uid` exist (from `BaseEntity`)
- [ ] `created_at`, `created_by`, `updated_at`, `updated_by` exist
- [ ] `is_active`, `version` exist

---

## 🔍 Quick Verification Commands

```bash
# Check table structure
make show-tables TABLES="table_name"

# Check migration status
make db-info

# View specific table
make db-shell
# Then: \d+ schema.table_name
```

---

## ⚠️ Common Mistakes

1. **Schema Mismatch:** Entity `@Table(schema="common_user")` but migration creates in `common_company`
2. **Missing NOT NULL:** Migration has `NOT NULL` but entity has `nullable = true`
3. **Column Name Mismatch:** Entity `@Column(name="user_id")` but migration has `userId`
4. **Missing Junction Table:** Entity has `@ManyToMany` but no junction table in migration
5. **Foreign Key Mismatch:** Entity `@JoinColumn` doesn't match migration `FOREIGN KEY`

---

**Last Updated:** 2025-11-06  
**Status:** Active


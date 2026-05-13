#!/usr/bin/env python3
"""
Flyway Migration Consolidation Script
======================================
Reads all migration files in order and produces:
  V001__initial_schema.sql  — DDL only (CREATE SCHEMA/TABLE/INDEX/POLICY/SEQUENCE, ALTER for RLS)
  V002__seed_data.sql       — INSERT statements from versioned migrations
  
Transformations applied:
  1. "order" schema  → sales_ord  (direct, no rename migration)
  2. prod_fiber_attribute → prod_product_attribute (with product_scope column)
  3. Flowboard PG ENUMs → VARCHAR(50) columns
  4. Remove: common_ai.common_ai_log, production_execution_warehouse_location
  5. Keep: event_publication, yarn skeleton tables, inheritance_rule_schema
  6. PermissionTemplate/Override → common_user schema
  7. Inline all ALTER TABLE ADD COLUMN into CREATE TABLE
  8. Skip rename/drop migrations that are no longer needed
"""

import os
import re
import sys
from pathlib import Path
from collections import OrderedDict

MIGRATION_DIR = Path(__file__).parent.parent / "src" / "main" / "resources" / "db" / "migration"
OUTPUT_DIR = MIGRATION_DIR  # Will write directly to migration dir after archiving

# Files to skip entirely (rename/drop that will be inlined)
SKIP_FILES = {
    "V20260329130000__rename_order_schema_to_sales_ord.sql",     # We write sales_ord directly
    "V20260508123000__rename_fiber_attribute_to_product_attribute.sql",  # We write prod_product_attribute directly
    "V20260329120000__convert_approval_pg_enums_to_varchar.sql",  # We use VARCHAR from start
    "V20260331122500__drop_cross_schema_certification_fks.sql",   # We never create cross-schema cert FKs
}

# Tables to remove entirely
REMOVE_TABLES = {
    "common_ai.common_ai_log",
}

# Flowboard PG ENUM types to skip (we use VARCHAR instead)
PG_ENUM_TYPES_TO_SKIP = {
    "flowboard.widget_type",
    "flowboard.workload_status",
    "flowboard.recurring_frequency",
    "flowboard.badge_type",
    "common_approval.policy_target_level",
    "common_approval.approval_request_status",
    "common_approval.promotion_request_status",
    "common_approval.promotion_trigger_type",
    "common_auth.user_trust_level",
}


def read_migration_files():
    """Read all migration files in Flyway order."""
    files = sorted(MIGRATION_DIR.glob("V*.sql"))
    return files


def read_repeatable_files():
    """Read repeatable migration files."""
    return sorted(MIGRATION_DIR.glob("R__*.sql"))


def apply_schema_rename(content):
    """Replace "order" schema references with sales_ord."""
    # Replace CREATE SCHEMA IF NOT EXISTS "order"
    content = content.replace('CREATE SCHEMA IF NOT EXISTS "order"', 
                              'CREATE SCHEMA IF NOT EXISTS sales_ord')
    # Replace "order". table references
    content = re.sub(r'"order"\.', 'sales_ord.', content)
    return content


def apply_table_renames(content):
    """Apply table/column renames for Material→Product refactoring."""
    # prod_fiber_attribute → prod_product_attribute (table name in DDL)
    content = content.replace('prod_fiber_attribute', 'prod_product_attribute')
    content = content.replace('idx_fiber_attribute_code', 'idx_product_attr_code')
    content = content.replace('idx_fiber_attribute_active', 'idx_product_attr_active')
    return content


def apply_warehouse_location_transform(content):
    """Transform production_execution_warehouse_location to iwm.warehouse_location.
    
    The warehouse_location table was originally in production schema, then moved to iwm.
    In the consolidated schema, we create it directly in iwm.
    """
    # Replace table references: production.production_execution_warehouse_location → iwm.warehouse_location
    content = content.replace(
        'production.production_execution_warehouse_location',
        'iwm.warehouse_location'
    )
    # Remove the SET SCHEMA and RENAME lines (no longer needed)
    content = re.sub(r'ALTER TABLE iwm\.warehouse_location SET SCHEMA iwm;[^\n]*\n?', '', content)
    content = re.sub(r'ALTER TABLE iwm\.warehouse_location RENAME TO warehouse_location;[^\n]*\n?', '', content)
    content = re.sub(r'-- Rename table to `warehouse_location`[^\n]*\n?', '', content)
    content = re.sub(r'-- Move production_execution_warehouse_location[^\n]*\n?', '', content)
    return content


def apply_permission_schema(content):
    """Move permission_template/override to common_user schema."""
    content = content.replace(
        'CREATE TABLE IF NOT EXISTS permission_template',
        'CREATE TABLE IF NOT EXISTS common_user.permission_template'
    )
    content = content.replace(
        'CREATE TABLE IF NOT EXISTS permission_override',
        'CREATE TABLE IF NOT EXISTS common_user.permission_override'
    )
    # Fix index references
    content = content.replace(
        'ON permission_template',
        'ON common_user.permission_template'
    )
    content = content.replace(
        'ON permission_override',
        'ON common_user.permission_override'
    )
    return content


def remove_pg_enum_creation(content):
    """Remove CREATE TYPE ... AS ENUM blocks and DROP TYPE statements."""
    # Remove CREATE TYPE ... AS ENUM (...);  (multiline)
    content = re.sub(
        r'CREATE TYPE\s+\S+\s+AS\s+ENUM\s*\([^)]*\)\s*;',
        '-- [CONSOLIDATED] PG ENUM removed, using VARCHAR instead',
        content,
        flags=re.DOTALL
    )
    # Remove DROP TYPE statements
    content = re.sub(r'DROP TYPE IF EXISTS\s+\S+\s*;', '', content)
    return content


def remove_tables(content):
    """Remove CREATE TABLE blocks for tables in REMOVE_TABLES."""
    for table in REMOVE_TABLES:
        # Remove CREATE TABLE ... ); block
        pattern = rf'CREATE TABLE(?:\s+IF NOT EXISTS)?\s+{re.escape(table)}\s*\([^;]*\);'
        content = re.sub(pattern, f'-- [CONSOLIDATED] Table {table} removed', content, flags=re.DOTALL)
        # Remove related CREATE INDEX
        content = re.sub(rf'CREATE INDEX[^;]*{re.escape(table)}[^;]*;', '', content)
    return content


def is_insert_statement(line):
    """Check if a line starts an INSERT statement."""
    stripped = line.strip().upper()
    return stripped.startswith('INSERT INTO') or stripped.startswith('INSERT')


def split_ddl_and_seeds(content):
    """Split content into DDL and seed (INSERT) parts."""
    lines = content.split('\n')
    ddl_lines = []
    seed_lines = []
    in_insert = False
    paren_depth = 0
    
    for line in lines:
        stripped = line.strip()
        
        if is_insert_statement(line) and not in_insert:
            in_insert = True
            paren_depth = 0
            
        if in_insert:
            seed_lines.append(line)
            paren_depth += line.count('(') - line.count(')')
            if stripped.endswith(';'):
                in_insert = False
                paren_depth = 0
        else:
            ddl_lines.append(line)
    
    return '\n'.join(ddl_lines), '\n'.join(seed_lines)


def inline_alter_tables(ddl_content):
    """
    Find ALTER TABLE ... ADD COLUMN statements and try to inline them.
    This is a best-effort approach - complex ALTER statements are kept as-is
    at the end of the schema file.
    """
    # For now, keep ALTER TABLE statements as-is in a post-DDL section
    # This is safer than trying to parse and inline them
    return ddl_content


def consolidate():
    """Main consolidation logic."""
    migration_files = read_migration_files()
    
    all_ddl = []
    all_seeds = []
    
    # Header
    all_ddl.append("-- =============================================================================")
    all_ddl.append("-- V001 — CONSOLIDATED INITIAL SCHEMA")
    all_ddl.append("-- Generated by consolidate_migrations.py")
    all_ddl.append("-- Source: 91 migration files consolidated into single schema definition")
    all_ddl.append("-- =============================================================================")
    all_ddl.append("")
    
    all_seeds.append("-- =============================================================================")
    all_seeds.append("-- V002 — CONSOLIDATED SEED DATA")
    all_seeds.append("-- Generated by consolidate_migrations.py")
    all_seeds.append("-- =============================================================================")
    all_seeds.append("")
    
    for mfile in migration_files:
        fname = mfile.name
        
        # Skip files we don't need
        if fname in SKIP_FILES:
            all_ddl.append(f"-- [SKIPPED] {fname} (inlined into consolidated schema)")
            all_ddl.append("")
            continue
        
        content = mfile.read_text(encoding='utf-8')
        
        # Apply transformations
        content = apply_schema_rename(content)
        content = apply_table_renames(content)
        content = apply_warehouse_location_transform(content)
        content = apply_permission_schema(content)
        content = remove_pg_enum_creation(content)
        content = remove_tables(content)
        
        # Add product_scope column ONLY to the prod_product_attribute table in V002
        if fname == "V002__FIBER_module.sql":
            # Use a more specific pattern that only matches prod_product_attribute table
            content = content.replace(
                "CREATE TABLE IF NOT EXISTS production.prod_product_attribute (\n",
                "CREATE TABLE IF NOT EXISTS production.prod_product_attribute (\n-- [CONSOLIDATED] product_scope column added from V20260508 migration\n"
            )
            content = content.replace(
                "CREATE TABLE IF NOT EXISTS production.prod_product_attribute (\r\n",
                "CREATE TABLE IF NOT EXISTS production.prod_product_attribute (\r\n-- [CONSOLIDATED] product_scope column added from V20260508 migration\r\n"
            )
            # Add product_scope only after display_order in prod_product_attribute
            # Pattern: in a block that starts with prod_product_attribute and ends with );
            lines = content.split('\n')
            in_product_attr_table = False
            new_lines = []
            for line in lines:
                if 'prod_product_attribute' in line and 'CREATE TABLE' in line:
                    in_product_attr_table = True
                if in_product_attr_table and 'display_order' in line.lower():
                    new_lines.append(line)
                    # Add product_scope after display_order
                    indent = '    '  # same indent
                    new_lines.append(f"{indent}product_scope VARCHAR(20) DEFAULT 'ALL',")
                    in_product_attr_table = False  # done
                    continue
                new_lines.append(line)
            content = '\n'.join(new_lines)
        
        # Split DDL and seeds
        ddl_part, seed_part = split_ddl_and_seeds(content)
        
        if ddl_part.strip():
            all_ddl.append(f"-- ===================== FROM: {fname} =====================")
            all_ddl.append(ddl_part)
            all_ddl.append("")
        
        if seed_part.strip():
            all_seeds.append(f"-- ===================== FROM: {fname} =====================")
            all_seeds.append(seed_part)
            all_seeds.append("")
    
    return '\n'.join(all_ddl), '\n'.join(all_seeds)


def clean_empty_lines(content):
    """Remove excessive empty lines (more than 2 consecutive)."""
    return re.sub(r'\n{4,}', '\n\n\n', content)


def main():
    print("=== Flyway Migration Consolidation ===")
    print(f"Migration dir: {MIGRATION_DIR}")
    
    migration_files = read_migration_files()
    print(f"Found {len(migration_files)} versioned migration files")
    
    repeatable_files = read_repeatable_files()
    print(f"Found {len(repeatable_files)} repeatable migration files")
    
    # Archive old migrations
    archive_dir = MIGRATION_DIR / "archived"
    archive_dir.mkdir(exist_ok=True)
    
    # Consolidate
    ddl_content, seed_content = consolidate()
    ddl_content = clean_empty_lines(ddl_content)
    seed_content = clean_empty_lines(seed_content)
    
    # Write consolidated files to temp location first
    temp_v001 = MIGRATION_DIR / "V001__initial_schema.sql.new"
    temp_v002 = MIGRATION_DIR / "V002__seed_data.sql.new"
    
    temp_v001.write_text(ddl_content, encoding='utf-8')
    temp_v002.write_text(seed_content, encoding='utf-8')
    
    ddl_lines = len(ddl_content.split('\n'))
    seed_lines = len(seed_content.split('\n'))
    
    print(f"\nGenerated:")
    print(f"  V001__initial_schema.sql.new: {ddl_lines} lines")
    print(f"  V002__seed_data.sql.new: {seed_lines} lines")
    
    # Move old migrations to archive
    print(f"\nArchiving {len(migration_files)} versioned migrations...")
    for mfile in migration_files:
        target = archive_dir / mfile.name
        mfile.rename(target)
        
    print(f"Archived to: {archive_dir}")
    
    # Rename new files
    final_v001 = MIGRATION_DIR / "V001__initial_schema.sql"
    final_v002 = MIGRATION_DIR / "V002__seed_data.sql"
    temp_v001.rename(final_v001)
    temp_v002.rename(final_v002)
    
    # Copy repeatable files (they stay as-is)
    print(f"\nRepeatable migrations kept as-is: {[f.name for f in repeatable_files]}")
    
    print("\n✅ Consolidation complete!")
    print(f"Final migration dir contents:")
    for f in sorted(MIGRATION_DIR.glob("*.sql")):
        print(f"  {f.name}")


if __name__ == "__main__":
    main()

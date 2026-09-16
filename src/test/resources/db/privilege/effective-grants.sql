-- DB-PRIV-1 read-only evidence query.
-- Keep the classification CASE aligned with TablePrivilegeClassification.
-- TablePrivilegeClassIT checks exact entries, the default, and live catalogue results.
-- Result set 1: effective table/view privileges for both runtime roles.
WITH catalogue_relations AS (
    SELECT c.oid AS relation_oid,
           n.nspname AS schema_name,
           c.relname AS relation_name,
           c.relkind
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('r', 'p', 'v', 'm')
      AND n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
),
classified_relations AS (
    SELECT relation_oid,
           schema_name,
           relation_name,
           relkind,
           CASE schema_name || '.' || relation_name
               WHEN 'production.quality_decision' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'production.quality_decision_unit' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales_ord.order_cover_evidence' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales.customer_commercial_assignment' THEN 'CLOSE_ONCE_LEDGER'
               WHEN 'production.production_execution_batch_color_archive' THEN 'READ_ONLY_ARCHIVE'
               WHEN 'common_tenant.flyway_schema_history' THEN 'NO_RUNTIME_ACCESS'
               WHEN 'public.jobrunr_migrations' THEN 'APP_ONLY_MUTABLE'
               WHEN 'public.jobrunr_jobs' THEN 'APP_ONLY_MUTABLE'
               WHEN 'public.jobrunr_recurring_jobs' THEN 'APP_ONLY_MUTABLE'
               WHEN 'public.jobrunr_backgroundjobservers' THEN 'APP_ONLY_MUTABLE'
               WHEN 'public.jobrunr_metadata' THEN 'APP_ONLY_MUTABLE'
               WHEN 'public.jobrunr_jobs_stats' THEN 'APP_ONLY_READ_ONLY'
               ELSE 'MUTABLE'
           END AS declared_class
    FROM catalogue_relations
),
runtime_roles(role_name) AS (
    VALUES ('fabric_app'::name), ('fabric_system'::name)
)
SELECT classified.schema_name,
       classified.relation_name,
       classified.relkind,
       classified.declared_class,
       runtime_role.role_name,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'SELECT')
           AS select_granted,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'INSERT')
           AS insert_granted,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'UPDATE')
           AS update_granted,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'DELETE')
           AS delete_granted,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'TRUNCATE')
           AS truncate_granted,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'REFERENCES')
           AS references_granted,
       has_table_privilege(runtime_role.role_name, classified.relation_oid, 'TRIGGER')
           AS trigger_granted,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'SELECT WITH GRANT OPTION')
           AS select_with_grant_option,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'INSERT WITH GRANT OPTION')
           AS insert_with_grant_option,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'UPDATE WITH GRANT OPTION')
           AS update_with_grant_option,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'DELETE WITH GRANT OPTION')
           AS delete_with_grant_option,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'TRUNCATE WITH GRANT OPTION')
           AS truncate_with_grant_option,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'REFERENCES WITH GRANT OPTION')
           AS references_with_grant_option,
       has_table_privilege(
           runtime_role.role_name, classified.relation_oid, 'TRIGGER WITH GRANT OPTION')
           AS trigger_with_grant_option
FROM classified_relations classified
CROSS JOIN runtime_roles runtime_role
ORDER BY classified.schema_name, classified.relation_name, runtime_role.role_name;

-- Result set 2: defaults are owner- and schema-specific inputs to DB-PRIV-2.
SELECT pg_get_userbyid(default_acl.defaclrole) AS owner_role,
       COALESCE(namespace.nspname, '<global>') AS schema_name,
       CASE default_acl.defaclobjtype
           WHEN 'r' THEN 'TABLES'
           WHEN 'S' THEN 'SEQUENCES'
           WHEN 'f' THEN 'FUNCTIONS'
           WHEN 'T' THEN 'TYPES'
           WHEN 'n' THEN 'SCHEMAS'
           ELSE default_acl.defaclobjtype::text
       END AS object_type,
       default_acl.defaclacl AS default_privileges
FROM pg_default_acl default_acl
LEFT JOIN pg_namespace namespace ON namespace.oid = default_acl.defaclnamespace
ORDER BY owner_role, schema_name, object_type;

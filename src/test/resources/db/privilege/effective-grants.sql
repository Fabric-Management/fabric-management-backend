-- Q0: connection context (run as migration owner or admin).
SELECT current_user                                        AS connected_role,
       r.rolsuper                                          AS connected_is_superuser,
       pg_get_userbyid(d.datdba)                           AS database_owner,
       o.rolsuper                                          AS database_owner_is_superuser,
       o.rolbypassrls                                      AS database_owner_bypasses_rls,
       current_setting('server_version')                   AS server_version,
       (SELECT version FROM common_tenant.flyway_schema_history
         WHERE success AND version IS NOT NULL
         ORDER BY installed_rank DESC LIMIT 1)              AS applied_version,
       (SELECT installed_by FROM common_tenant.flyway_schema_history
         WHERE success ORDER BY installed_rank DESC LIMIT 1) AS last_installed_by,
       (SELECT string_agg(DISTINCT installed_by, ', ')
          FROM common_tenant.flyway_schema_history)        AS all_installers
FROM pg_database d
JOIN pg_roles r ON r.rolname = current_user
JOIN pg_roles o ON o.oid = d.datdba
WHERE d.datname = current_database();

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
               WHEN 'flowboard.routing_pool' THEN 'MUTABLE'
               WHEN 'flowboard.routing_pool_member' THEN 'MUTABLE'
               WHEN 'flowboard.routing_task_state' THEN 'MUTABLE_SYSTEM_PURGE'
               WHEN 'flowboard.routing_failure' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'flowboard.routing_failure_resolution' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'flowboard.routing_failure_alert' THEN 'MUTABLE_SYSTEM_PURGE'
               WHEN 'flowboard.decision_follow' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'flowboard.decision_follow_suppression' THEN 'MUTABLE'
               WHEN 'flowboard.decision_subject_projection' THEN 'MUTABLE'
               WHEN 'production.quality_decision' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'production.quality_decision_unit' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales_ord.requirement_profile_version' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales_ord.order_cover_evidence' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales_ord.order_cover_evidence_stream' THEN 'MUTABLE_SYSTEM_PURGE'
               WHEN 'sales_ord.order_cover_activation' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales_ord.order_cover_case' THEN 'MUTABLE_SYSTEM_PURGE'
               WHEN 'sales_ord.order_cover_case_line' THEN 'MUTABLE_SYSTEM_PURGE'
               WHEN 'sales_ord.order_cover_result' THEN 'APPEND_ONLY_LEDGER'
               WHEN 'sales_ord.order_cover_line_result' THEN 'APPEND_ONLY_LEDGER'
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

-- Q2: exploded default ACLs, including non-table objects.
SELECT pg_get_userbyid(d.defaclrole)                                   AS owner_role,
       COALESCE(n.nspname, '<global>')                                 AS schema_name,
       CASE d.defaclobjtype
           WHEN 'r' THEN 'TABLES'   WHEN 'S' THEN 'SEQUENCES'
           WHEN 'f' THEN 'FUNCTIONS' WHEN 'T' THEN 'TYPES'
           WHEN 'n' THEN 'SCHEMAS'  ELSE d.defaclobjtype::text
       END                                                             AS object_type,
       CASE WHEN a.grantee = 0 THEN 'PUBLIC'
            ELSE pg_get_userbyid(a.grantee) END                        AS grantee,
       a.privilege_type                                                AS privilege,
       a.is_grantable                                                  AS is_grantable
FROM pg_default_acl d
LEFT JOIN pg_namespace n ON n.oid = d.defaclnamespace
CROSS JOIN LATERAL aclexplode(d.defaclacl) a
ORDER BY owner_role, schema_name, object_type, grantee, privilege;

-- Q3: runtime role membership closure (PostgreSQL 16).
WITH RECURSIVE closure(runtime_role, member_oid, parent_oid, depth, inherit_option, set_option, path) AS (
    SELECT r.rolname, m.member, m.roleid, 1, m.inherit_option, m.set_option,
           ARRAY[m.member, m.roleid]
    FROM pg_roles r
    JOIN pg_auth_members m ON m.member = r.oid
    WHERE r.rolname IN ('fabric_app', 'fabric_system')
  UNION ALL
    SELECT c.runtime_role, m.member, m.roleid, c.depth + 1, m.inherit_option, m.set_option,
           c.path || m.roleid
    FROM closure c
    JOIN pg_auth_members m ON m.member = c.parent_oid
    WHERE m.roleid <> ALL (c.path)
)
SELECT runtime_role,
       pg_get_userbyid(member_oid) AS member_role,
       pg_get_userbyid(parent_oid) AS parent_role,
       depth, inherit_option, set_option
FROM closure
ORDER BY runtime_role, depth, member_role, parent_role;

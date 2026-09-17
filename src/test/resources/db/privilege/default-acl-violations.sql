WITH RECURSIVE runtime AS (
    SELECT oid FROM pg_roles WHERE rolname IN ('fabric_app', 'fabric_system')
), ancestors(role_oid, path) AS (
    SELECT m.roleid, ARRAY[m.member, m.roleid]
    FROM pg_auth_members m
    JOIN runtime r ON r.oid = m.member
  UNION ALL
    SELECT m.roleid, a.path || m.roleid
    FROM ancestors a
    JOIN pg_auth_members m ON m.member = a.role_oid
    WHERE m.roleid <> ALL (a.path)
), forbidden(grantee_oid) AS (
    SELECT oid FROM runtime
  UNION
    SELECT role_oid FROM ancestors
  UNION
    SELECT 0::oid
)
SELECT pg_get_userbyid(d.defaclrole)                    AS owner_role,
       COALESCE(n.nspname, '<global>')                  AS schema_name,
       CASE WHEN a.grantee = 0 THEN 'PUBLIC'
            ELSE pg_get_userbyid(a.grantee) END         AS grantee,
       a.privilege_type                                 AS privilege,
       a.is_grantable                                   AS is_grantable
FROM pg_default_acl d
LEFT JOIN pg_namespace n ON n.oid = d.defaclnamespace
CROSS JOIN LATERAL aclexplode(d.defaclacl) a
WHERE d.defaclobjtype = 'r'
  AND a.grantee IN (SELECT grantee_oid FROM forbidden)
ORDER BY owner_role, schema_name, grantee, privilege, is_grantable;

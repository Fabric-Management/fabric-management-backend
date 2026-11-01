-- Two safeguards for permanent tenant deletion (V20261031120000).
--
-- 1. Only an operator can purge, never the running application. The backend logs in as
--    fabric_system, and purge_tenant needs fabric_system (BYPASSRLS, and the role the append-only
--    guards accept), so the role alone cannot tell them apart. The login can: purge_tenant now
--    refuses a session whose login (session_user) is fabric_system. An operator logs in as a role
--    that is a member of fabric_system, or as a superuser, and runs SET ROLE fabric_system first;
--    scripts/db/provision-roles.sql creates fabric_operator for that. A scheduled purge inside the
--    backend would need such a login of its own.
--
-- 2. A login identity that still has memberships can no longer be deleted. The membership
--    foreign key cascaded, so deleting an identity silently removed that person's memberships in
--    every tenant. No application code deletes identities; purge_tenant deletes only those left
--    with no membership, and now a mistake there fails instead of reaching other tenants.

ALTER TABLE common_auth.membership
    DROP CONSTRAINT fk_membership_login_identity,
    ADD CONSTRAINT fk_membership_login_identity
        FOREIGN KEY (login_identity_id) REFERENCES common_auth.login_identity(id);

CREATE OR REPLACE FUNCTION common_tenant.purge_tenant(
    p_tenant_id UUID,
    p_confirm_name TEXT,
    p_apply BOOLEAN DEFAULT false)
RETURNS TABLE(step TEXT, table_name TEXT, row_count BIGINT)
LANGUAGE plpgsql
SET search_path = pg_catalog, pg_temp
AS $function$
DECLARE
    v_name TEXT;
    v_type TEXT;
    v_active BOOLEAN;
    v_tables regclass[];
    v_remaining regclass[];
    v_ready regclass[];
    v_table regclass;
    v_count BIGINT;
    v_total BIGINT := 0;
    v_blockers BIGINT := 0;
    v_identities UUID[];
    v_settings TEXT[];
    v_setting TEXT;
    v_fk RECORD;
    v_join TEXT;
BEGIN
    IF NOT (current_user = 'fabric_system'
            OR EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND rolsuper)) THEN
        RAISE EXCEPTION 'purge_tenant must run as fabric_system, not %', current_user
            USING ERRCODE = '42501';
    END IF;
    -- The application logs in as fabric_system; a person switches to it. The application's own
    -- session can never change its session_user, so this keeps every code path of the running
    -- backend away from the purge.
    IF session_user = 'fabric_system' THEN
        RAISE EXCEPTION 'purge_tenant is for operators: log in as an operator role and '
            'SET ROLE fabric_system; the application''s own fabric_system login may not purge'
            USING ERRCODE = '42501';
    END IF;
    IF p_tenant_id = '00000000-0000-0000-0000-000000000000'::uuid THEN
        RAISE EXCEPTION 'the platform tenant is never purged' USING ERRCODE = '55000';
    END IF;

    IF p_apply THEN
        BEGIN
            SELECT t.name, t.type, t.is_active INTO v_name, v_type, v_active
            FROM common_tenant.common_tenant t WHERE t.id = p_tenant_id
            FOR UPDATE NOWAIT;
        EXCEPTION WHEN lock_not_available THEN
            RAISE EXCEPTION 'tenant % is locked by another transaction (a purge in progress?)',
                p_tenant_id USING ERRCODE = '55P03';
        END;
    ELSE
        SELECT t.name, t.type, t.is_active INTO v_name, v_type, v_active
        FROM common_tenant.common_tenant t WHERE t.id = p_tenant_id;
    END IF;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'tenant % does not exist', p_tenant_id USING ERRCODE = 'P0002';
    END IF;
    IF v_type = 'TEMPLATE' THEN
        RAISE EXCEPTION 'tenant % is a TEMPLATE and is never purged', p_tenant_id
            USING ERRCODE = '55000';
    END IF;
    IF p_confirm_name IS DISTINCT FROM v_name THEN
        RAISE EXCEPTION 'confirmation does not match the tenant name of %', p_tenant_id
            USING ERRCODE = '22023';
    END IF;
    IF p_apply AND v_active IS DISTINCT FROM false THEN
        RAISE EXCEPTION 'tenant % is still active; deactivate it (is_active = false) and commit '
            'before purging', p_tenant_id USING ERRCODE = '55000';
    END IF;

    -- Every permanent ordinary or partitioned table that carries a tenant_id column. Temporary
    -- tables (another session's included) are not tenant data.
    SELECT array_agg(c.oid::regclass ORDER BY n.nspname, c.relname) INTO v_tables
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
    WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition AND c.relpersistence <> 't'
      AND n.nspname NOT IN ('pg_catalog', 'information_schema');

    -- The foreign keys through which another tenant's row can point at this tenant's rows are
    -- the tenant-blind ones: a key that includes tenant_id cannot cross tenants. Both loops below
    -- read them from pg_constraint.

    IF p_apply THEN
        -- Lock first, check second. A row lock on the referenced rows makes a concurrent insert
        -- that points at them wait (and fail once they are gone); where the caller may not lock
        -- rows of the parent (an append-only table without UPDATE), the referencing table is
        -- locked against writes instead. The tenant row itself is already locked.
        FOR v_fk IN
            SELECT DISTINCT f.child, f.parent,
                   has_table_privilege(current_user, f.parent, 'UPDATE') AS can_lock_rows
            FROM (
            SELECT con.conname, con.conrelid::regclass AS child, con.confrelid::regclass AS parent,
                   con.conkey, con.confkey
            FROM pg_constraint con
            WHERE con.contype = 'f'
              AND con.conrelid = ANY (v_tables)
              AND (con.confrelid = ANY (v_tables)
                   OR con.confrelid = 'common_tenant.common_tenant'::regclass)
              AND NOT EXISTS (
                  SELECT 1 FROM unnest(con.conkey) k
                  JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k
                  WHERE a.attname = 'tenant_id')) f
            WHERE f.parent <> 'common_tenant.common_tenant'::regclass
        LOOP
            IF v_fk.can_lock_rows THEN
                EXECUTE format('SELECT 1 FROM %s WHERE tenant_id = $1 FOR UPDATE', v_fk.parent)
                    USING p_tenant_id;
            ELSE
                EXECUTE format('LOCK TABLE %s IN SHARE MODE', v_fk.child);
            END IF;
        END LOOP;
        PERFORM 1 FROM common_auth.login_identity li
        WHERE li.id IN (SELECT m.login_identity_id FROM common_auth.membership m
                        WHERE m.tenant_id = p_tenant_id)
        FOR UPDATE OF li;
    END IF;

    FOR v_fk IN
        SELECT f.* FROM (
            SELECT con.conname, con.conrelid::regclass AS child, con.confrelid::regclass AS parent,
                   con.conkey, con.confkey
            FROM pg_constraint con
            WHERE con.contype = 'f'
              AND con.conrelid = ANY (v_tables)
              AND (con.confrelid = ANY (v_tables)
                   OR con.confrelid = 'common_tenant.common_tenant'::regclass)
              AND NOT EXISTS (
                  SELECT 1 FROM unnest(con.conkey) k
                  JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k
                  WHERE a.attname = 'tenant_id')) f
        ORDER BY f.child::text, f.conname
    LOOP
        SELECT string_agg(format('c.%I = p.%I', ca.attname, pa.attname), ' AND ')
        INTO v_join
        FROM unnest(v_fk.conkey, v_fk.confkey) AS k(child_col, parent_col)
        JOIN pg_attribute ca ON ca.attrelid = v_fk.child AND ca.attnum = k.child_col
        JOIN pg_attribute pa ON pa.attrelid = v_fk.parent AND pa.attnum = k.parent_col;

        IF v_fk.parent = 'common_tenant.common_tenant'::regclass THEN
            EXECUTE format(
                'SELECT count(*) FROM %s c JOIN common_tenant.common_tenant p ON %s '
                'WHERE p.id = $1 AND c.tenant_id IS DISTINCT FROM $1', v_fk.child, v_join)
            INTO v_count USING p_tenant_id;
        ELSE
            EXECUTE format(
                'SELECT count(*) FROM %s c JOIN %s p ON %s '
                'WHERE p.tenant_id = $1 AND c.tenant_id IS DISTINCT FROM $1',
                v_fk.child, v_fk.parent, v_join)
            INTO v_count USING p_tenant_id;
        END IF;
        IF v_count > 0 THEN
            v_blockers := v_blockers + v_count;
            step := 'blocked: another tenant references this one';
            table_name := format('%s (%s)', v_fk.child, v_fk.conname);
            row_count := v_count;
            RETURN NEXT;
        END IF;
    END LOOP;
    IF v_blockers > 0 AND p_apply THEN
        RAISE EXCEPTION '% row(s) of other tenants reference tenant %; nothing was deleted '
            '(run with p_apply = false for the list)', v_blockers, p_tenant_id
            USING ERRCODE = '23503';
    END IF;

    SELECT array_agg(DISTINCT m.login_identity_id) INTO v_identities
    FROM common_auth.membership m WHERE m.tenant_id = p_tenant_id;

    IF p_apply THEN
        -- Open the append-only tables for this tenant only, using the settings their guards read.
        SELECT array_agg(DISTINCT s) INTO v_settings
        FROM (SELECT (regexp_matches(p.prosrc,
                        'current_setting\(''(app\.[a-z0-9_]+_purge_tenant)''', 'g'))[1] AS s
              FROM pg_trigger tg JOIN pg_proc p ON p.oid = tg.tgfoid
              WHERE NOT tg.tgisinternal) found;
        FOREACH v_setting IN ARRAY coalesce(v_settings, '{}') LOOP
            PERFORM set_config(v_setting, p_tenant_id::text, true);
        END LOOP;
        SET CONSTRAINTS ALL DEFERRED;
    END IF;

    -- Children before parents: a table is ready once no remaining table still references it
    -- through a non-deferrable foreign key. Self-references are emptied by one statement.
    v_remaining := v_tables;
    WHILE cardinality(v_remaining) > 0 LOOP
        SELECT array_agg(t) INTO v_ready
        FROM unnest(v_remaining) AS t
        WHERE NOT EXISTS (
            SELECT 1 FROM pg_constraint con
            WHERE con.contype = 'f' AND con.confrelid = t AND con.conrelid <> t
              AND NOT con.condeferrable AND con.conrelid = ANY (v_remaining));
        IF v_ready IS NULL THEN
            RAISE EXCEPTION 'no delete order exists among %', v_remaining USING ERRCODE = '55000';
        END IF;
        FOREACH v_table IN ARRAY v_ready LOOP
            IF p_apply THEN
                EXECUTE format('DELETE FROM %s WHERE tenant_id = $1', v_table) USING p_tenant_id;
                GET DIAGNOSTICS v_count = ROW_COUNT;
            ELSE
                EXECUTE format('SELECT count(*) FROM %s WHERE tenant_id = $1', v_table)
                INTO v_count USING p_tenant_id;
            END IF;
            IF v_count > 0 THEN
                v_total := v_total + v_count;
                step := CASE WHEN p_apply THEN 'deleted' ELSE 'would delete' END;
                table_name := v_table::text;
                row_count := v_count;
                RETURN NEXT;
            END IF;
        END LOOP;
        v_remaining := ARRAY(SELECT x FROM unnest(v_remaining) AS x WHERE x <> ALL (v_ready));
    END LOOP;

    -- Login identities that no tenant uses any more (locked above, so a membership added
    -- elsewhere meanwhile is seen here and keeps its identity).
    IF p_apply THEN
        DELETE FROM common_auth.login_identity li
        WHERE li.id = ANY (coalesce(v_identities, '{}'))
          AND NOT EXISTS (SELECT 1 FROM common_auth.membership m WHERE m.login_identity_id = li.id);
        GET DIAGNOSTICS v_count = ROW_COUNT;
    ELSE
        SELECT count(*) INTO v_count FROM common_auth.login_identity li
        WHERE li.id = ANY (coalesce(v_identities, '{}'))
          AND NOT EXISTS (SELECT 1 FROM common_auth.membership m
                          WHERE m.login_identity_id = li.id AND m.tenant_id IS DISTINCT FROM p_tenant_id);
    END IF;
    step := CASE WHEN p_apply THEN 'deleted' ELSE 'would delete' END;
    table_name := 'common_auth.login_identity (no membership left)';
    row_count := v_count;
    RETURN NEXT;

    IF p_apply THEN
        DELETE FROM common_tenant.common_tenant WHERE id = p_tenant_id;
        -- Deferred foreign keys are checked now rather than at the caller's commit, and the
        -- append-only tables close again for the rest of the caller's transaction.
        SET CONSTRAINTS ALL IMMEDIATE;
        FOREACH v_setting IN ARRAY coalesce(v_settings, '{}') LOOP
            PERFORM set_config(v_setting, '', true);
        END LOOP;
        -- Nothing of the tenant may remain anywhere.
        FOREACH v_table IN ARRAY v_tables LOOP
            EXECUTE format('SELECT count(*) FROM %s WHERE tenant_id = $1', v_table)
            INTO v_count USING p_tenant_id;
            IF v_count > 0 THEN
                RAISE EXCEPTION '% row(s) of tenant % remain in %; rolled back', v_count,
                    p_tenant_id, v_table USING ERRCODE = '55000';
            END IF;
        END LOOP;
    END IF;
    step := CASE WHEN p_apply THEN 'deleted'
                 WHEN v_blockers > 0 THEN 'blocked'
                 ELSE 'would delete' END;
    table_name := format('common_tenant.common_tenant (%s, %s rows in tenant tables)', v_name, v_total);
    row_count := 1;
    RETURN NEXT;
END;
$function$;

REVOKE ALL ON FUNCTION common_tenant.purge_tenant(UUID, TEXT, BOOLEAN) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_system') THEN
        GRANT EXECUTE ON FUNCTION common_tenant.purge_tenant(UUID, TEXT, BOOLEAN) TO fabric_system;
    END IF;
END $$;

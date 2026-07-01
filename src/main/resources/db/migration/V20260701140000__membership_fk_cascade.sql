-- IDENTITY-2: membership rows belong to tenant lifecycle for hard deletes/TTL cleanup.
-- Keep common_auth.membership RLS-free; only change the tenant FK action.

DO $$
BEGIN
  IF to_regclass('common_auth.membership') IS NOT NULL THEN
    ALTER TABLE common_auth.membership DROP CONSTRAINT IF EXISTS fk_membership_tenant;

    IF NOT EXISTS (
      SELECT 1
      FROM pg_constraint c
      JOIN pg_class cls ON cls.oid = c.conrelid
      JOIN pg_namespace ns ON ns.oid = cls.relnamespace
      WHERE ns.nspname = 'common_auth'
        AND cls.relname = 'membership'
        AND c.conname = 'fk_membership_tenant'
    ) THEN
      ALTER TABLE common_auth.membership ADD CONSTRAINT fk_membership_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES common_tenant.common_tenant(id)
        ON DELETE CASCADE;
    END IF;

    EXECUTE $comment$
      COMMENT ON COLUMN common_auth.membership.tenant_id IS
        'Plain FK to common_tenant.common_tenant(id) with ON DELETE CASCADE; deliberately not an RLS isolation column.'
    $comment$;
  END IF;
END $$;

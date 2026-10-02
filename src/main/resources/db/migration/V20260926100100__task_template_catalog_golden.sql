-- TASK-TEMPLATE-TENANCY-1 (M2): place the five adapter-served task templates in golden-template
-- with their catalogue keys (ticket §5). Applies the V20260603142000 rule: SYSTEM_TENANT_ID is a
-- deny-by-default sentinel; shared reference data lives in golden-template and is copied per tenant.
--
-- Per entry: skip if golden already holds the key; else adopt exactly one golden candidate that
-- carries the full seed signature; else move exactly one SYSTEM row that carries it. Anything
-- else aborts (R3 on golden, or drift of the SYSTEM source row). The SYSTEM duplicate of an
-- adopted entry is left untouched.
--
-- Seed signature: catalog_key NULL, created_by NULL, updated_by NULL, version 0, deleted_at NULL,
-- description NULL, pinned name, pinned uid rule, pinned content fingerprint (same expression as
-- the INV-RECIPE-TASK-1 inventory, and as TaskTemplateCatalogue in Java).
SET LOCAL row_security = off;

DO $$
DECLARE
  golden CONSTANT uuid := '00000000-0000-0000-ffff-000000000001';
  system_tenant CONSTANT uuid := '00000000-0000-0000-0000-000000000000';
  p record;
  n integer;
  cand uuid;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM common_tenant.common_tenant WHERE id = golden) THEN
    RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: golden-template tenant % not found', golden;
  END IF;

  FOR p IN
    SELECT * FROM (VALUES
      ('SALES_ORDER_CONFIRMED__PLANNING', 'SalesOrderConfirmed', 'PLANNING',
       'Auto Template', 'NULL', 'eb736401146175ef43672b5d89d51e2a'),
      ('WORK_ORDER_APPROVED__PRODUCTION', 'WorkOrderApproved', 'PRODUCTION',
       'Auto Template', 'NULL', '5fee86f6508c7fcba300a72e2cf2db8b'),
      ('GOODS_RECEIPT_CONFIRMED__WAREHOUSE', 'GoodsReceiptConfirmed', 'WAREHOUSE',
       'Auto Template', 'NULL', 'b69878f426ea372145af021508b02e71'),
      ('QUOTE_SEND_REQUESTED__APPROVAL', 'QuoteSendRequested', 'APPROVAL',
       'Quote send approval', 'ANY', 'f60aeab289657754c5cb2521cefc6431'),
      ('WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT', 'WorkOrderRecipeAssignmentNeeded',
       'RECIPE_ASSIGNMENT', 'Recipe Assignment Required', 'FIXED:SYS-TMPL-RECIPE-ASSIGN',
       '12428190ed4797c168cf7e4a0ab6c451')
    ) AS v(catalog_key, event_type, task_type, name, uid_rule, fingerprint)
  LOOP
    IF EXISTS (SELECT 1 FROM flowboard.task_template
                WHERE tenant_id = golden AND catalog_key = p.catalog_key) THEN
      CONTINUE;
    END IF;

    SELECT count(*) INTO n FROM flowboard.task_template
     WHERE tenant_id = golden AND catalog_key IS NULL
       AND event_type = p.event_type AND task_type = p.task_type;

    IF n > 1 THEN
      RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: % golden candidates for %, expected at most one',
        n, p.catalog_key;
    END IF;

    IF n = 1 THEN
      cand := NULL;
      SELECT t.id INTO cand FROM flowboard.task_template t
       WHERE t.tenant_id = golden AND t.catalog_key IS NULL
         AND t.event_type = p.event_type AND t.task_type = p.task_type
         AND t.created_by IS NULL AND t.updated_by IS NULL AND t.version = 0
         AND t.deleted_at IS NULL AND t.description IS NULL AND t.name = p.name
         AND md5(concat_ws('|', t.title_template, t.task_type, t.module_type, t.default_priority,
                           t.default_assignee_role, t.estimated_hours, t.auto_labels,
                           t.checklist_template)) = p.fingerprint
         AND (p.uid_rule = 'ANY'
              OR (p.uid_rule = 'NULL' AND t.uid IS NULL)
              OR (p.uid_rule LIKE 'FIXED:%' AND t.uid = substr(p.uid_rule, 7)));
      IF cand IS NULL THEN
        RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: golden candidate for % lacks the seed signature',
          p.catalog_key;
      END IF;
      UPDATE flowboard.task_template SET catalog_key = p.catalog_key WHERE id = cand;
      CONTINUE;
    END IF;

    SELECT count(*) INTO n FROM flowboard.task_template
     WHERE tenant_id = system_tenant AND catalog_key IS NULL
       AND event_type = p.event_type AND task_type = p.task_type;
    IF n <> 1 THEN
      RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: % SYSTEM source rows for %, expected exactly one',
        n, p.catalog_key;
    END IF;

    cand := NULL;
    SELECT t.id INTO cand FROM flowboard.task_template t
     WHERE t.tenant_id = system_tenant AND t.catalog_key IS NULL
       AND t.event_type = p.event_type AND t.task_type = p.task_type
       AND t.created_by IS NULL AND t.updated_by IS NULL AND t.version = 0
       AND t.deleted_at IS NULL AND t.description IS NULL AND t.name = p.name
       AND md5(concat_ws('|', t.title_template, t.task_type, t.module_type, t.default_priority,
                         t.default_assignee_role, t.estimated_hours, t.auto_labels,
                         t.checklist_template)) = p.fingerprint
       AND (p.uid_rule = 'ANY'
            OR (p.uid_rule = 'NULL' AND t.uid IS NULL)
            OR (p.uid_rule LIKE 'FIXED:%' AND t.uid = substr(p.uid_rule, 7)));
    IF cand IS NULL THEN
      RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: SYSTEM source row for % drifted from the seed signature',
        p.catalog_key;
    END IF;

    UPDATE flowboard.task_template
       SET tenant_id = golden, catalog_key = p.catalog_key
     WHERE id = cand;
  END LOOP;

  SELECT count(*) INTO n FROM flowboard.task_template
   WHERE tenant_id = golden AND catalog_key IS NOT NULL;
  IF n <> 5 THEN
    RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: golden holds % keyed templates, expected 5', n;
  END IF;

  IF EXISTS (SELECT 1 FROM flowboard.task_template
              WHERE tenant_id = system_tenant AND catalog_key IS NOT NULL) THEN
    RAISE EXCEPTION 'TASK-TEMPLATE-TENANCY-1: SYSTEM tenant holds a keyed template';
  END IF;
END $$;

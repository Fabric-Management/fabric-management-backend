-- Seed future tenants through the golden TEMPLATE tenant.
INSERT INTO i18n.translation_key
    (id, tenant_id, key_code, module, default_value, description)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'notification.stuck_follow_up_resolved.title', 'NOTIFICATION',
     'Sorted — {entityRef} is fixed', 'Resolved stuck follow-up notification title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'notification.stuck_follow_up_resolved.body', 'NOTIFICATION',
     'The follow-up for {entityRef} didn''t complete earlier, but it''s now resolved. Please try again.',
     'Resolved stuck follow-up notification body')
ON CONFLICT (tenant_id, key_code) DO NOTHING;

INSERT INTO i18n.translation_value
    (id, tenant_id, translation_key_id, locale, value, is_override)
SELECT gen_random_uuid(),
       '00000000-0000-0000-ffff-000000000001',
       tk.id,
       'TR',
       CASE tk.key_code
         WHEN 'notification.stuck_follow_up_resolved.title'
           THEN 'Çözüldü — {entityRef} düzeltildi'
         WHEN 'notification.stuck_follow_up_resolved.body'
           THEN '{entityRef} için takip işlemi daha önce tamamlanamamıştı, ancak sorun artık çözüldü. Lütfen tekrar deneyin.'
       END,
       FALSE
FROM i18n.translation_key tk
WHERE tk.tenant_id = '00000000-0000-0000-ffff-000000000001'
  AND tk.key_code IN (
    'notification.stuck_follow_up_resolved.title',
    'notification.stuck_follow_up_resolved.body'
  )
ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'STUCK_FOLLOW_UP_RESOLVED', 'IN_APP',
     'notification.stuck_follow_up_resolved.title',
     'notification.stuck_follow_up_resolved.body',
     'HIGH', 'INSTANT')
ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

-- Backfill the two keys into every existing real tenant.
INSERT INTO i18n.translation_key
    (id, tenant_id, uid, key_code, module, default_value, description,
     is_active, created_at, updated_at, version)
SELECT gen_random_uuid(),
       tenant.id,
       gen_random_uuid()::varchar,
       source_key.key_code,
       source_key.module,
       source_key.default_value,
       source_key.description,
       source_key.is_active,
       now(),
       now(),
       0
FROM common_tenant.common_tenant tenant
CROSS JOIN i18n.translation_key source_key
WHERE tenant.type NOT IN ('TEMPLATE', 'SYSTEM')
  AND source_key.tenant_id = '00000000-0000-0000-ffff-000000000001'
  AND source_key.key_code IN (
    'notification.stuck_follow_up_resolved.title',
    'notification.stuck_follow_up_resolved.body'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM i18n.translation_key destination_key
    WHERE destination_key.tenant_id = tenant.id
      AND destination_key.key_code = source_key.key_code
  );

-- Backfill the IN_APP template into every existing real tenant.
INSERT INTO notification.notification_template
    (id, tenant_id, uid, event_type, channel, title_key, body_key, importance,
     delivery_type, grouping_window_minutes, is_active, created_at, updated_at, version)
SELECT gen_random_uuid(),
       tenant.id,
       gen_random_uuid()::varchar,
       source_template.event_type,
       source_template.channel,
       source_template.title_key,
       source_template.body_key,
       source_template.importance,
       source_template.delivery_type,
       source_template.grouping_window_minutes,
       source_template.is_active,
       now(),
       now(),
       0
FROM common_tenant.common_tenant tenant
CROSS JOIN notification.notification_template source_template
WHERE tenant.type NOT IN ('TEMPLATE', 'SYSTEM')
  AND source_template.tenant_id = '00000000-0000-0000-ffff-000000000001'
  AND source_template.event_type = 'STUCK_FOLLOW_UP_RESOLVED'
  AND source_template.channel = 'IN_APP'
  AND NOT EXISTS (
    SELECT 1
    FROM notification.notification_template destination_template
    WHERE destination_template.tenant_id = tenant.id
      AND destination_template.event_type = source_template.event_type
      AND destination_template.channel = source_template.channel
  );

-- Backfill TR values, remapping each source key to the destination tenant's key ID.
INSERT INTO i18n.translation_value
    (id, tenant_id, uid, translation_key_id, locale, value, is_override,
     is_active, created_at, updated_at, version)
SELECT gen_random_uuid(),
       tenant.id,
       gen_random_uuid()::varchar,
       destination_key.id,
       source_value.locale,
       source_value.value,
       source_value.is_override,
       source_value.is_active,
       now(),
       now(),
       0
FROM common_tenant.common_tenant tenant
JOIN i18n.translation_key source_key
  ON source_key.tenant_id = '00000000-0000-0000-ffff-000000000001'
 AND source_key.key_code IN (
   'notification.stuck_follow_up_resolved.title',
   'notification.stuck_follow_up_resolved.body'
 )
JOIN i18n.translation_value source_value
  ON source_value.tenant_id = '00000000-0000-0000-ffff-000000000001'
 AND source_value.translation_key_id = source_key.id
JOIN i18n.translation_key destination_key
  ON destination_key.tenant_id = tenant.id
 AND destination_key.key_code = source_key.key_code
WHERE tenant.type NOT IN ('TEMPLATE', 'SYSTEM')
  AND NOT EXISTS (
    SELECT 1
    FROM i18n.translation_value destination_value
    WHERE destination_value.tenant_id = tenant.id
      AND destination_value.translation_key_id = destination_key.id
      AND destination_value.locale = source_value.locale
  );

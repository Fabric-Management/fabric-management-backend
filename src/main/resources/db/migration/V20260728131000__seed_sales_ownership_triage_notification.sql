-- Sales ownership triage notifications (RSF-3b).
-- OPENED: a customer with open quotes has no eligible owner; in-app to sales managers.
-- AGED:   the case outlived the tenant threshold; in-app to everyone holding sales:assign-owner,
--         plus e-mail as an optional channel governed by each user's notification preference.
--         Both stay inside the tenant (no platform-level recipient).
-- Seed future tenants through the golden TEMPLATE tenant.
INSERT INTO i18n.translation_key
    (id, tenant_id, key_code, module, default_value, description)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'notification.customer_ownership_triage_opened.title', 'NOTIFICATION',
     '{customerName} needs a sales rep',
     'New customer ownership triage case notification title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'notification.customer_ownership_triage_opened.body', 'NOTIFICATION',
     '{customerName} has no sales rep right now, and {unassignedQuoteCount} open quote(s) '
       || 'have no owner. Assign someone.',
     'New customer ownership triage case notification body'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'notification.customer_ownership_triage_aged.title', 'NOTIFICATION',
     '{customerName} is still waiting for a sales rep',
     'Overdue customer ownership triage case notification title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'notification.customer_ownership_triage_aged.body', 'NOTIFICATION',
     '{customerName} has had no sales rep for {ageDuration}, and {unassignedQuoteCount} open '
       || 'quote(s) are waiting. Assign someone on the Sales assignments screen.',
     'Overdue customer ownership triage case notification body')
ON CONFLICT (tenant_id, key_code) DO NOTHING;

INSERT INTO i18n.translation_value
    (id, tenant_id, translation_key_id, locale, value, is_override)
SELECT gen_random_uuid(),
       '00000000-0000-0000-ffff-000000000001',
       translation_key.id,
       'TR',
       CASE translation_key.key_code
         WHEN 'notification.customer_ownership_triage_opened.title'
           THEN '{customerName} için satış temsilcisi gerekiyor'
         WHEN 'notification.customer_ownership_triage_opened.body'
           THEN '{customerName} müşterisinin şu an satış temsilcisi yok ve {unassignedQuoteCount} açık '
             || 'teklifi kimseye bağlı değil. Bir temsilci atayın.'
         WHEN 'notification.customer_ownership_triage_aged.title'
           THEN '{customerName} hâlâ satış temsilcisi bekliyor'
         WHEN 'notification.customer_ownership_triage_aged.body'
           THEN '{customerName} {ageDuration} süredir satış temsilcisiz ve {unassignedQuoteCount} açık teklif '
             || 'bekliyor. Satış atamaları ekranından birini atayın.'
       END,
       FALSE
FROM i18n.translation_key translation_key
WHERE translation_key.tenant_id = '00000000-0000-0000-ffff-000000000001'
  AND translation_key.key_code IN (
    'notification.customer_ownership_triage_opened.title',
    'notification.customer_ownership_triage_opened.body',
    'notification.customer_ownership_triage_aged.title',
    'notification.customer_ownership_triage_aged.body'
  )
ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'CUSTOMER_OWNERSHIP_TRIAGE_OPENED', 'IN_APP',
     'notification.customer_ownership_triage_opened.title',
     'notification.customer_ownership_triage_opened.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'CUSTOMER_OWNERSHIP_TRIAGE_AGED', 'IN_APP',
     'notification.customer_ownership_triage_aged.title',
     'notification.customer_ownership_triage_aged.body',
     'HIGH', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
     'CUSTOMER_OWNERSHIP_TRIAGE_AGED', 'EMAIL',
     'notification.customer_ownership_triage_aged.title',
     'notification.customer_ownership_triage_aged.body',
     'HIGH', 'INSTANT')
ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

-- An overdue case waits for the people who can resolve it: its e-mail is on by default (B1).
UPDATE notification.notification_template
   SET enabled_by_default = TRUE
 WHERE tenant_id = '00000000-0000-0000-ffff-000000000001'
   AND event_type = 'CUSTOMER_OWNERSHIP_TRIAGE_AGED'
   AND channel = 'EMAIL';

-- The platform's own tenant is not an operational tenant, but its type is REGULAR (there is no
-- SYSTEM tenant type), so it is excluded by id; without this it received these templates too.
-- Backfill keys into every existing operational tenant.
INSERT INTO i18n.translation_key
    (id, tenant_id, uid, key_code, module, default_value, description,
     is_active, created_at, updated_at, version)
SELECT gen_random_uuid(),
       tenant.id,
       gen_random_uuid()::VARCHAR,
       source_key.key_code,
       source_key.module,
       source_key.default_value,
       source_key.description,
       source_key.is_active,
       NOW(),
       NOW(),
       0
FROM common_tenant.common_tenant tenant
CROSS JOIN i18n.translation_key source_key
WHERE tenant.type NOT IN ('TEMPLATE', 'SYSTEM')
  AND tenant.id <> '00000000-0000-0000-0000-000000000000'
  AND source_key.tenant_id = '00000000-0000-0000-ffff-000000000001'
  AND source_key.key_code IN (
    'notification.customer_ownership_triage_opened.title',
    'notification.customer_ownership_triage_opened.body',
    'notification.customer_ownership_triage_aged.title',
    'notification.customer_ownership_triage_aged.body'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM i18n.translation_key destination_key
    WHERE destination_key.tenant_id = tenant.id
      AND destination_key.key_code = source_key.key_code
  );

-- Backfill the notification template into every existing operational tenant.
INSERT INTO notification.notification_template
    (id, tenant_id, uid, event_type, channel, title_key, body_key, importance,
     delivery_type, grouping_window_minutes, enabled_by_default, is_active, created_at,
     updated_at, version)
SELECT gen_random_uuid(),
       tenant.id,
       gen_random_uuid()::VARCHAR,
       source_template.event_type,
       source_template.channel,
       source_template.title_key,
       source_template.body_key,
       source_template.importance,
       source_template.delivery_type,
       source_template.grouping_window_minutes,
       source_template.enabled_by_default,
       source_template.is_active,
       NOW(),
       NOW(),
       0
FROM common_tenant.common_tenant tenant
CROSS JOIN notification.notification_template source_template
WHERE tenant.type NOT IN ('TEMPLATE', 'SYSTEM')
  AND tenant.id <> '00000000-0000-0000-0000-000000000000'
  AND source_template.tenant_id = '00000000-0000-0000-ffff-000000000001'
  AND source_template.event_type IN (
    'CUSTOMER_OWNERSHIP_TRIAGE_OPENED',
    'CUSTOMER_OWNERSHIP_TRIAGE_AGED'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM notification.notification_template destination_template
    WHERE destination_template.tenant_id = tenant.id
      AND destination_template.event_type = source_template.event_type
      AND destination_template.channel = source_template.channel
  );

-- Backfill translations while remapping to each tenant's key IDs.
INSERT INTO i18n.translation_value
    (id, tenant_id, uid, translation_key_id, locale, value, is_override,
     is_active, created_at, updated_at, version)
SELECT gen_random_uuid(),
       tenant.id,
       gen_random_uuid()::VARCHAR,
       destination_key.id,
       source_value.locale,
       source_value.value,
       source_value.is_override,
       source_value.is_active,
       NOW(),
       NOW(),
       0
FROM common_tenant.common_tenant tenant
JOIN i18n.translation_key source_key
  ON source_key.tenant_id = '00000000-0000-0000-ffff-000000000001'
 AND source_key.key_code IN (
   'notification.customer_ownership_triage_opened.title',
   'notification.customer_ownership_triage_opened.body',
   'notification.customer_ownership_triage_aged.title',
   'notification.customer_ownership_triage_aged.body'
 )
JOIN i18n.translation_value source_value
  ON source_value.tenant_id = '00000000-0000-0000-ffff-000000000001'
 AND source_value.translation_key_id = source_key.id
JOIN i18n.translation_key destination_key
  ON destination_key.tenant_id = tenant.id
 AND destination_key.key_code = source_key.key_code
WHERE tenant.type NOT IN ('TEMPLATE', 'SYSTEM')
  AND tenant.id <> '00000000-0000-0000-0000-000000000000'
  AND NOT EXISTS (
    SELECT 1
    FROM i18n.translation_value destination_value
    WHERE destination_value.tenant_id = tenant.id
      AND destination_value.translation_key_id = destination_key.id
      AND destination_value.locale = source_value.locale
  );


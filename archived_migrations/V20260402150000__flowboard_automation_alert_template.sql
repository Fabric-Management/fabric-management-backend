DO $$
DECLARE
  sys_tenant UUID := '00000000-0000-0000-0000-000000000001'::UUID;
BEGIN

  -- =========================================================
  -- 1. NOTIFICATION TEMPLATE
  -- =========================================================

  INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
  VALUES
    (gen_random_uuid(), sys_tenant,
     'AUTOMATION_ALERT', 'IN_APP',
     'notification.automation_alert.title', 'notification.automation_alert.body',
     'NORMAL', 'INSTANT')
  ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

  -- =========================================================
  -- 2. TRANSLATION KEYS
  -- =========================================================

  INSERT INTO i18n.translation_key (id, tenant_id, key_code, module, default_value, description)
  VALUES
    (gen_random_uuid(), sys_tenant, 'notification.automation_alert.title', 'NOTIFICATION',
     'Automation Alert!', 'AutomationAlertRequested title'),
    (gen_random_uuid(), sys_tenant, 'notification.automation_alert.body', 'NOTIFICATION',
     '{message}', 'AutomationAlertRequested body')
  ON CONFLICT (key_code) DO NOTHING;

  -- =========================================================
  -- 3. TR TRANSLATIONS
  -- =========================================================

  INSERT INTO i18n.translation_value (id, tenant_id, translation_key_id, locale, value, is_override)
  SELECT gen_random_uuid(), sys_tenant, tk.id, 'TR',
    CASE tk.key_code
      WHEN 'notification.automation_alert.title' THEN 'Otomasyon Uyarısı!'
      WHEN 'notification.automation_alert.body'  THEN '{message}'
    END,
    FALSE
  FROM i18n.translation_key tk
  WHERE tk.key_code IN (
    'notification.automation_alert.title', 'notification.automation_alert.body'
  )
  ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

END $$;

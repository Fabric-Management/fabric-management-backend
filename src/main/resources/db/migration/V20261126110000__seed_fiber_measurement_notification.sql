-- Seed the golden tenant for future onboarding and backfill existing operational tenants.
DO $fiber_measurement_notification_role$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = current_user AND (rolsuper OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'FIBER-QC-2 notification seed requires an active SUPERUSER or BYPASSRLS database role'
            USING ERRCODE = '42501';
    END IF;
END;
$fiber_measurement_notification_role$;

INSERT INTO i18n.translation_key
    (tenant_id, key_code, module, default_value)
SELECT tenant.id, wording.key_code, 'NOTIFICATION', wording.english
FROM common_tenant.common_tenant tenant
CROSS JOIN (VALUES
    ('notification.batch_measurement_out_of_limits.title', 'Batch {batchCode} has a measurement outside its limits'),
    ('notification.batch_measurement_out_of_limits.body', 'The {parameterLabel} measurement for batch {batchCode} is outside its limits.[[ The number of other measurements outside their limits is {additionalCount}.]]'),
    ('notification.label.parameter.FINENESS', 'fineness'),
    ('notification.label.parameter.LENGTH', 'staple length'),
    ('notification.label.parameter.STRENGTH', 'strength'),
    ('notification.label.parameter.ELONGATION', 'elongation'),
    ('notification.label.parameter.MOISTURE', 'moisture'),
    ('notification.label.parameter.TRASH_CONTENT', 'trash content'),
    ('notification.label.parameter.UNIFORMITY_INDEX', 'uniformity index')
) AS wording(key_code, english)
WHERE tenant.deleted_at IS NULL
  AND tenant.id <> '00000000-0000-0000-0000-000000000000'
  AND (tenant.type <> 'TEMPLATE' OR tenant.id = '00000000-0000-0000-ffff-000000000001')
ON CONFLICT (tenant_id, key_code) DO NOTHING;

INSERT INTO i18n.translation_value
    (tenant_id, translation_key_id, locale, value, is_override)
SELECT key.tenant_id, key.id, language.locale,
       CASE language.locale WHEN 'EN' THEN key.default_value ELSE wording.turkish END, FALSE
FROM i18n.translation_key key
JOIN (VALUES
    ('notification.batch_measurement_out_of_limits.title', '{batchCode} partisinde sınır dışında bir ölçüm var'),
    ('notification.batch_measurement_out_of_limits.body', '{batchCode} partisinin {parameterLabel} ölçümü sınırlarının dışında.[[ Sınır dışında kalan diğer ölçümlerin sayısı {additionalCount}.]]'),
    ('notification.label.parameter.FINENESS', 'incelik'),
    ('notification.label.parameter.LENGTH', 'lif uzunluğu'),
    ('notification.label.parameter.STRENGTH', 'mukavemet'),
    ('notification.label.parameter.ELONGATION', 'uzama'),
    ('notification.label.parameter.MOISTURE', 'nem'),
    ('notification.label.parameter.TRASH_CONTENT', 'safsızlık oranı'),
    ('notification.label.parameter.UNIFORMITY_INDEX', 'uzunluk düzgünlüğü')
) AS wording(key_code, turkish) ON key.key_code = wording.key_code
JOIN common_tenant.common_tenant tenant ON tenant.id = key.tenant_id
CROSS JOIN (VALUES ('EN'), ('TR')) AS language(locale)
WHERE tenant.deleted_at IS NULL
  AND tenant.id <> '00000000-0000-0000-0000-000000000000'
  AND (tenant.type <> 'TEMPLATE' OR tenant.id = '00000000-0000-0000-ffff-000000000001')
ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

-- E-mail is off by default for all recipients and is available by preference.
INSERT INTO notification.notification_template
    (tenant_id, event_type, channel, title_key, body_key, importance, delivery_type, enabled_by_default)
SELECT tenant.id, 'BATCH_MEASUREMENT_OUT_OF_LIMITS', channel.name,
       'notification.batch_measurement_out_of_limits.title',
       'notification.batch_measurement_out_of_limits.body',
       'HIGH', 'INSTANT', FALSE
FROM common_tenant.common_tenant tenant
CROSS JOIN (VALUES ('IN_APP'), ('EMAIL')) AS channel(name)
WHERE tenant.deleted_at IS NULL
  AND tenant.id <> '00000000-0000-0000-0000-000000000000'
  AND (tenant.type <> 'TEMPLATE' OR tenant.id = '00000000-0000-0000-ffff-000000000001')
ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

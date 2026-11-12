-- NOTIF-UNIFY-1 S2: plain-language wording and the notifications moved to the hub.
--
-- 1. Labels and hints (golden tenant). A template never shows a raw code: the payload carries the
--    code (entityType = QUOTE) and the renderer reads notification.label.<name>.<CODE> for a
--    {nameLabel} placeholder and notification.hint.<name>.<CODE> for {nameHint}.
-- 2. The tenant-level notifications that used to be written straight into the legacy store
--    (concession review, missing quality standard, expiring certificates, fibre request decisions),
--    seeded in the golden tenant like every other hub event.
-- 3. The platform-level notifications (fibre request submitted, new company), seeded in the
--    platform's own (system) tenant: they go to platform administrators only (B4) and are
--    delivered there, so their wording and plan labels live there too.
--
-- Only the system and golden tenants exist when the migrations run (development period: the
-- database is rebuilt), so nothing is backfilled; new tenants copy the golden tenant's
-- notification.% keys and templates at onboarding.

-- Golden tenant: labels, hints and the moved tenant-level events
INSERT INTO i18n.translation_key
    (id, tenant_id, key_code, module, default_value, description)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.WORK_ORDER', 'NOTIFICATION',
     'Work order', 'Notification wording for entityType=WORK_ORDER'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.RECIPE_CREATE', 'NOTIFICATION',
     'Recipe', 'Notification wording for entityType=RECIPE_CREATE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.CRITICAL_ACTION', 'NOTIFICATION',
     'Critical action', 'Notification wording for entityType=CRITICAL_ACTION'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.PURCHASE_ORDER', 'NOTIFICATION',
     'Purchase order', 'Notification wording for entityType=PURCHASE_ORDER'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.SALES_ORDER', 'NOTIFICATION',
     'Sales order', 'Notification wording for entityType=SALES_ORDER'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.QUOTE', 'NOTIFICATION',
     'Quote', 'Notification wording for entityType=QUOTE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.entityType.BATCH', 'NOTIFICATION',
     'Batch', 'Notification wording for entityType=BATCH'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.direction.INBOUND', 'NOTIFICATION',
     'money in', 'Notification wording for direction=INBOUND'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.direction.OUTBOUND', 'NOTIFICATION',
     'money out', 'Notification wording for direction=OUTBOUND'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.currentStage.ESTIMATED', 'NOTIFICATION',
     'estimated', 'Notification wording for currentStage=ESTIMATED'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.currentStage.PLANNED', 'NOTIFICATION',
     'planned', 'Notification wording for currentStage=PLANNED'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.currentStage.ACTUAL', 'NOTIFICATION',
     'actual', 'Notification wording for currentStage=ACTUAL'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.previousStage.ESTIMATED', 'NOTIFICATION',
     'estimated', 'Notification wording for previousStage=ESTIMATED'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.previousStage.PLANNED', 'NOTIFICATION',
     'planned', 'Notification wording for previousStage=PLANNED'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.previousStage.ACTUAL', 'NOTIFICATION',
     'actual', 'Notification wording for previousStage=ACTUAL'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.difference.HIGHER', 'NOTIFICATION',
     'higher', 'Notification wording for difference=HIGHER'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.difference.LOWER', 'NOTIFICATION',
     'lower', 'Notification wording for difference=LOWER'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.invoiceSide.RECEIVABLE', 'NOTIFICATION',
     'Sales invoice', 'Notification wording for invoiceSide=RECEIVABLE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.invoiceSide.PAYABLE', 'NOTIFICATION',
     'Purchase invoice', 'Notification wording for invoiceSide=PAYABLE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.hint.invoiceSide.RECEIVABLE', 'NOTIFICATION',
     'The payment hasn''t arrived yet; contact the customer.', 'Notification wording for invoiceSide=RECEIVABLE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.hint.invoiceSide.PAYABLE', 'NOTIFICATION',
     'Pay it or contact the supplier.', 'Notification wording for invoiceSide=PAYABLE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.reason.BATCH_COMPOSITION_UNKNOWN', 'NOTIFICATION',
     'the batch''s blend is unknown', 'Notification wording for reason=BATCH_COMPOSITION_UNKNOWN'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.reason.FIBER_PRODUCT_NOT_FOUND', 'NOTIFICATION',
     'the batch''s product has no fibre definition', 'Notification wording for reason=FIBER_PRODUCT_NOT_FOUND'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.reason.QUALITY_PROFILE_UNAVAILABLE', 'NOTIFICATION',
     'the batch''s quality profile is no longer in use', 'Notification wording for reason=QUALITY_PROFILE_UNAVAILABLE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.reason.QUALITY_PROFILE_NOT_APPLICABLE', 'NOTIFICATION',
     'the batch''s quality profile doesn''t match its blend', 'Notification wording for reason=QUALITY_PROFILE_NOT_APPLICABLE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.label.reason.NO_QUALITY_PROFILE', 'NOTIFICATION',
     'there is no quality profile for this fibre and blend', 'Notification wording for reason=NO_QUALITY_PROFILE'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.batch_concession_review.title', 'NOTIFICATION',
     'Batch {batchCode} needs approval to be accepted with conditions', 'BATCH_CONCESSION_REVIEW title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.batch_concession_review.body', 'NOTIFICATION',
     'Batch {batchCode} passed testing with conditions. Someone with authority needs to approve this before the batch can be used.', 'BATCH_CONCESSION_REVIEW body'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.batch_no_quality_standard.title', 'NOTIFICATION',
     'No quality standard for batch {batchCode}', 'BATCH_NO_QUALITY_STANDARD title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.batch_no_quality_standard.body', 'NOTIFICATION',
     'No quality profile fits {target}: {reasonLabel}. Review the test result yourself.', 'BATCH_NO_QUALITY_STANDARD body'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.batch_certification_expiring.title', 'NOTIFICATION',
     'Batch certificates expiring soon', 'BATCH_CERTIFICATION_EXPIRING title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.batch_certification_expiring.body', 'NOTIFICATION',
     '{count} batch certificate(s) expire by {thresholdDate}. If you want to keep selling these batches as certified (e.g. GOTS), renew the certificates.', 'BATCH_CERTIFICATION_EXPIRING body'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.fiber_request_approved.title', 'NOTIFICATION',
     'Your fibre request was approved: {fiberName}', 'FIBER_REQUEST_APPROVED title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.fiber_request_approved.body', 'NOTIFICATION',
     '{fiberName} ({isoCode}) has been added to the catalogue and is ready to use.', 'FIBER_REQUEST_APPROVED body'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.fiber_request_rejected.title', 'NOTIFICATION',
     'Your fibre request was declined: {fiberName}', 'FIBER_REQUEST_REJECTED title'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'notification.fiber_request_rejected.body', 'NOTIFICATION',
     '{fiberName} ({isoCode}) was not added to the catalogue.[[ Reason: {reviewNote}]]', 'FIBER_REQUEST_REJECTED body')
ON CONFLICT (tenant_id, key_code) DO NOTHING;

INSERT INTO i18n.translation_value
    (id, tenant_id, translation_key_id, locale, value, is_override)
SELECT gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', translation_key.id, 'TR', wording.value, FALSE
FROM (VALUES
    ('notification.label.entityType.WORK_ORDER', 'İş emri'),
    ('notification.label.entityType.RECIPE_CREATE', 'Reçete'),
    ('notification.label.entityType.CRITICAL_ACTION', 'Kritik işlem'),
    ('notification.label.entityType.PURCHASE_ORDER', 'Satın alma siparişi'),
    ('notification.label.entityType.SALES_ORDER', 'Satış siparişi'),
    ('notification.label.entityType.QUOTE', 'Teklif'),
    ('notification.label.entityType.BATCH', 'Parti'),
    ('notification.label.direction.INBOUND', 'tahsilat'),
    ('notification.label.direction.OUTBOUND', 'ödeme'),
    ('notification.label.currentStage.ESTIMATED', 'tahmini'),
    ('notification.label.currentStage.PLANNED', 'planlanan'),
    ('notification.label.currentStage.ACTUAL', 'gerçekleşen'),
    ('notification.label.previousStage.ESTIMATED', 'tahmini'),
    ('notification.label.previousStage.PLANNED', 'planlanan'),
    ('notification.label.previousStage.ACTUAL', 'gerçekleşen'),
    ('notification.label.difference.HIGHER', 'yüksek'),
    ('notification.label.difference.LOWER', 'düşük'),
    ('notification.label.invoiceSide.RECEIVABLE', 'satış faturasının'),
    ('notification.label.invoiceSide.PAYABLE', 'alış faturasının'),
    ('notification.hint.invoiceSide.RECEIVABLE', 'Ödeme henüz gelmedi; müşteriyle görüşün.'),
    ('notification.hint.invoiceSide.PAYABLE', 'Ödemeyi yapın ya da tedarikçiyle görüşün.'),
    ('notification.label.reason.BATCH_COMPOSITION_UNKNOWN', 'partinin karışımı bilinmiyor'),
    ('notification.label.reason.FIBER_PRODUCT_NOT_FOUND', 'partinin ürününde elyaf tanımı yok'),
    ('notification.label.reason.QUALITY_PROFILE_UNAVAILABLE', 'partinin kalite profili artık kullanılmıyor'),
    ('notification.label.reason.QUALITY_PROFILE_NOT_APPLICABLE', 'partinin kalite profili karışımıyla uyuşmuyor'),
    ('notification.label.reason.NO_QUALITY_PROFILE', 'bu elyaf ve karışım için kalite profili yok'),
    ('notification.batch_concession_review.title', '{batchCode} partisi şartlı kabul onayı bekliyor'),
    ('notification.batch_concession_review.body', '{batchCode} partisi testten şartlı geçti. Kullanılabilmesi için yetkili birinin şartlı kabulü onaylaması gerekiyor.'),
    ('notification.batch_no_quality_standard.title', '{batchCode} partisi için kalite standardı yok'),
    ('notification.batch_no_quality_standard.body', '{target} için uygun kalite profili bulunamadı: {reasonLabel}. Test sonucunu elle değerlendirin.'),
    ('notification.batch_certification_expiring.title', 'Sertifikası dolmak üzere partiler var'),
    ('notification.batch_certification_expiring.body', '{count} partinin sertifikası {thresholdDate} tarihine kadar doluyor. Bu partileri sertifikalı (ör. GOTS) satmaya devam etmek istiyorsanız sertifikaları yenileyin.'),
    ('notification.fiber_request_approved.title', 'Elyaf talebiniz onaylandı: {fiberName}'),
    ('notification.fiber_request_approved.body', '{fiberName} ({isoCode}) kataloğa eklendi, artık kullanabilirsiniz.'),
    ('notification.fiber_request_rejected.title', 'Elyaf talebiniz reddedildi: {fiberName}'),
    ('notification.fiber_request_rejected.body', '{fiberName} ({isoCode}) kataloğa eklenmedi.[[ Neden: {reviewNote}]]')
) AS wording(key_code, value)
JOIN i18n.translation_key translation_key
  ON translation_key.tenant_id = '00000000-0000-0000-ffff-000000000001' AND translation_key.key_code = wording.key_code
ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

-- In-app is always delivered; e-mail is there for users who switch it on (B1: off by default).
INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'BATCH_CONCESSION_REVIEW', 'IN_APP',
     'notification.batch_concession_review.title', 'notification.batch_concession_review.body', 'HIGH', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'BATCH_CONCESSION_REVIEW', 'EMAIL',
     'notification.batch_concession_review.title', 'notification.batch_concession_review.body', 'HIGH', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'BATCH_NO_QUALITY_STANDARD', 'IN_APP',
     'notification.batch_no_quality_standard.title', 'notification.batch_no_quality_standard.body', 'HIGH', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'BATCH_NO_QUALITY_STANDARD', 'EMAIL',
     'notification.batch_no_quality_standard.title', 'notification.batch_no_quality_standard.body', 'HIGH', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'BATCH_CERTIFICATION_EXPIRING', 'IN_APP',
     'notification.batch_certification_expiring.title', 'notification.batch_certification_expiring.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'BATCH_CERTIFICATION_EXPIRING', 'EMAIL',
     'notification.batch_certification_expiring.title', 'notification.batch_certification_expiring.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'FIBER_REQUEST_APPROVED', 'IN_APP',
     'notification.fiber_request_approved.title', 'notification.fiber_request_approved.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'FIBER_REQUEST_APPROVED', 'EMAIL',
     'notification.fiber_request_approved.title', 'notification.fiber_request_approved.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'FIBER_REQUEST_REJECTED', 'IN_APP',
     'notification.fiber_request_rejected.title', 'notification.fiber_request_rejected.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001', 'FIBER_REQUEST_REJECTED', 'EMAIL',
     'notification.fiber_request_rejected.title', 'notification.fiber_request_rejected.body', 'NORMAL', 'INSTANT')
ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

-- System tenant: platform administrators' notifications
INSERT INTO i18n.translation_key
    (id, tenant_id, key_code, module, default_value, description)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.label.plan.TRIAL', 'NOTIFICATION',
     'trial', 'Notification wording for plan=TRIAL'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.label.plan.PLAYGROUND', 'NOTIFICATION',
     'demo', 'Notification wording for plan=PLAYGROUND'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.label.plan.SALES_LED', 'NOTIFICATION',
     'talk to sales', 'Notification wording for plan=SALES_LED'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.fiber_request_submitted.title', 'NOTIFICATION',
     'New fibre request: {fiberName}', 'FIBER_REQUEST_SUBMITTED title'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.fiber_request_submitted.body', 'NOTIFICATION',
     '{tenantName} asked for {fiberName} ({isoCode}) to be added to the catalogue. Review it and approve or reject.', 'FIBER_REQUEST_SUBMITTED body'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.new_tenant_onboarded.title', 'NOTIFICATION',
     'New company joined: {tenantName}', 'NEW_TENANT_ONBOARDED title'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'notification.new_tenant_onboarded.body', 'NOTIFICATION',
     '{tenantName} opened a new account: {planLabel}.', 'NEW_TENANT_ONBOARDED body')
ON CONFLICT (tenant_id, key_code) DO NOTHING;

INSERT INTO i18n.translation_value
    (id, tenant_id, translation_key_id, locale, value, is_override)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000000', translation_key.id, 'TR', wording.value, FALSE
FROM (VALUES
    ('notification.label.plan.TRIAL', 'deneme'),
    ('notification.label.plan.PLAYGROUND', 'demo'),
    ('notification.label.plan.SALES_LED', 'satış ekibiyle görüşme'),
    ('notification.fiber_request_submitted.title', 'Yeni elyaf talebi: {fiberName}'),
    ('notification.fiber_request_submitted.body', '{tenantName}, {fiberName} ({isoCode}) elyafının kataloğa eklenmesini istiyor. İnceleyip onaylayın ya da reddedin.'),
    ('notification.new_tenant_onboarded.title', 'Yeni şirket katıldı: {tenantName}'),
    ('notification.new_tenant_onboarded.body', '{tenantName} yeni bir hesap açtı: {planLabel}.')
) AS wording(key_code, value)
JOIN i18n.translation_key translation_key
  ON translation_key.tenant_id = '00000000-0000-0000-0000-000000000000' AND translation_key.key_code = wording.key_code
ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING;

-- In-app is always delivered; e-mail is there for users who switch it on (B1: off by default).
INSERT INTO notification.notification_template
    (id, tenant_id, event_type, channel, title_key, body_key, importance, delivery_type)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'FIBER_REQUEST_SUBMITTED', 'IN_APP',
     'notification.fiber_request_submitted.title', 'notification.fiber_request_submitted.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'FIBER_REQUEST_SUBMITTED', 'EMAIL',
     'notification.fiber_request_submitted.title', 'notification.fiber_request_submitted.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'NEW_TENANT_ONBOARDED', 'IN_APP',
     'notification.new_tenant_onboarded.title', 'notification.new_tenant_onboarded.body', 'NORMAL', 'INSTANT'),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000000', 'NEW_TENANT_ONBOARDED', 'EMAIL',
     'notification.new_tenant_onboarded.title', 'notification.new_tenant_onboarded.body', 'NORMAL', 'INSTANT')
ON CONFLICT (tenant_id, event_type, channel) DO NOTHING;

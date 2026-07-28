DELETE FROM notification.notification_template
WHERE event_type = 'CUSTOMER_OWNERSHIP_TRIAGE_OPENED';

DELETE FROM i18n.translation_value
WHERE translation_key_id IN (
    SELECT id
    FROM i18n.translation_key
    WHERE key_code IN (
        'notification.customer_ownership_triage_opened.title',
        'notification.customer_ownership_triage_opened.body'
    )
);

DELETE FROM i18n.translation_key
WHERE key_code IN (
    'notification.customer_ownership_triage_opened.title',
    'notification.customer_ownership_triage_opened.body'
);

-- Alter default columns for i18n configs to eliminate TR defaults for new rows

ALTER TABLE i18n.tenant_locale_config
    ALTER COLUMN default_locale SET DEFAULT 'EN',
    ALTER COLUMN supported_locales SET DEFAULT '["EN"]',
    ALTER COLUMN timezone SET DEFAULT 'Europe/London';

ALTER TABLE i18n.user_locale_config
    ALTER COLUMN locale SET DEFAULT 'EN';

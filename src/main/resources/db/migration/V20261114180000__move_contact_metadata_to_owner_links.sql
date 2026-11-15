SET LOCAL row_security = off;

ALTER TABLE common_company.common_organization_contact
    ADD COLUMN label varchar(100),
    ADD COLUMN is_personal boolean NOT NULL DEFAULT false;

ALTER TABLE common_user.common_user_contact
    ADD COLUMN label varchar(100),
    ADD COLUMN is_personal boolean NOT NULL DEFAULT true;

UPDATE common_company.common_organization_contact link
SET label = contact.label, is_personal = contact.is_personal
FROM common_communication.common_contact contact
WHERE contact.id = link.contact_id AND contact.tenant_id = link.tenant_id;

UPDATE common_user.common_user_contact link
SET label = contact.label, is_personal = contact.is_personal
FROM common_communication.common_contact contact
WHERE contact.id = link.contact_id AND contact.tenant_id = link.tenant_id;

ALTER TABLE common_communication.common_contact
    DROP COLUMN label,
    DROP COLUMN is_personal;


-- Keep the operator replay function compatible with the new owner-link schema.
DO $migration$
DECLARE
    definition text := pg_get_functiondef('common_company.backfill_onboarding_evidence()'::regprocedure);
BEGIN
    EXECUTE replace(
        replace(definition, 'NOT d.is_personal', 'NOT oc.is_personal'),
        'AND d.is_personal HAVING', 'AND uc.is_personal HAVING');
END
$migration$;

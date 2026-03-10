-- 1. Migrate `is_primary` from Address to UserAddress and OrganizationAddress
UPDATE common_user.common_user_address ua
SET is_primary = true
FROM common_communication.common_address a
WHERE ua.address_id = a.id AND a.is_primary = true;

UPDATE common_company.common_organization_address oa
SET is_primary = true
FROM common_communication.common_address a
WHERE oa.address_id = a.id AND a.is_primary = true;

-- 2. Migrate `is_primary` from Contact to UserContact and OrganizationContact
UPDATE common_user.common_user_contact uc
SET is_default = true
FROM common_communication.common_contact c
WHERE uc.contact_id = c.id AND c.is_primary = true;

UPDATE common_company.common_organization_contact oc
SET is_default = true
FROM common_communication.common_contact c
WHERE oc.contact_id = c.id AND c.is_primary = true;

-- 3. Drop redundant columns
ALTER TABLE common_communication.common_contact DROP COLUMN IF EXISTS is_primary;
ALTER TABLE common_communication.common_address DROP COLUMN IF EXISTS is_primary;

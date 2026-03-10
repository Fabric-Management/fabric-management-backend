-- Migration to remove legacy/unnormalized fields from common_user.user and human_employee.employee

-- 1. Remove redundancy and legacy fields from `user` table
ALTER TABLE common_user.common_user DROP COLUMN IF EXISTS department;
ALTER TABLE common_user.common_user DROP COLUMN IF EXISTS contact_value;
ALTER TABLE common_user.common_user DROP COLUMN IF EXISTS contact_type;
ALTER TABLE common_user.common_user DROP COLUMN IF EXISTS display_name;

-- 2. Remove secondary UID from `employee` table since it causes dual ID confusion
-- Depending on whether employee_number is retained, the string hex uid `uid` is dropped.
ALTER TABLE human.human_employee DROP COLUMN IF EXISTS uid;

-- rollback:
-- ALTER TABLE common_user.common_user ADD COLUMN department VARCHAR(255);
-- ALTER TABLE common_user.common_user ADD COLUMN contact_value VARCHAR(255);
-- ALTER TABLE common_user.common_user ADD COLUMN contact_type VARCHAR(50);
-- ALTER TABLE common_user.common_user ADD COLUMN display_name VARCHAR(255);
-- ALTER TABLE human.human_employee ADD COLUMN uid VARCHAR(50);

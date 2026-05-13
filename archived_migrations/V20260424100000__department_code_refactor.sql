-- ============================================================================
-- V20260424100000: Department Code Refactor
-- ============================================================================
-- Standardizes department codes to single-word, no-suffix format.
-- Removes umbrella departments (PRODUCTION, MANAGEMENT).
-- Adds department_group column for UI grouping (not part of RBAC).
-- Adds new production sub-departments: FIBER, KNITTING, GARMENT.
-- ============================================================================

-- 1. Add department_group column
ALTER TABLE common_company.common_department
    ADD COLUMN IF NOT EXISTS department_group VARCHAR(20);

-- 2. Rename existing codes (old → new)
UPDATE common_company.common_department SET department_code = 'YARN'      WHERE department_code = 'YARN_PRODUCTION';
UPDATE common_company.common_department SET department_code = 'DYEING'    WHERE department_code = 'DYEING_FINISHING';
UPDATE common_company.common_department SET department_code = 'HR'        WHERE department_code = 'HUMAN_RESOURCES';
UPDATE common_company.common_department SET department_code = 'FINANCE'   WHERE department_code = 'FINANCE_ACCOUNTING';
UPDATE common_company.common_department SET department_code = 'SALES'     WHERE department_code = 'SALES_MARKETING';
UPDATE common_company.common_department SET department_code = 'WAREHOUSE' WHERE department_code = 'WAREHOUSE_LOGISTICS';
UPDATE common_company.common_department SET department_code = 'QUALITY'   WHERE department_code = 'QUALITY_CONTROL';

-- 2b. Sync department_name to match new canonical names (UserSeeder looks up by name)
UPDATE common_company.common_department SET department_name = 'Human Resources'      WHERE department_code = 'HR';
UPDATE common_company.common_department SET department_name = 'Finance & Accounting' WHERE department_code = 'FINANCE';
UPDATE common_company.common_department SET department_name = 'Sales & Marketing'    WHERE department_code = 'SALES';
UPDATE common_company.common_department SET department_name = 'Procurement'          WHERE department_code = 'PROCUREMENT';
UPDATE common_company.common_department SET department_name = 'Quality Control'      WHERE department_code = 'QUALITY';
UPDATE common_company.common_department SET department_name = 'Warehouse & Logistics' WHERE department_code = 'WAREHOUSE';
UPDATE common_company.common_department SET department_name = 'Yarn Production'      WHERE department_code = 'YARN';
UPDATE common_company.common_department SET department_name = 'Weaving'              WHERE department_code = 'WEAVING';
UPDATE common_company.common_department SET department_name = 'Dyeing & Finishing'   WHERE department_code = 'DYEING';

-- 3. Assign groups
UPDATE common_company.common_department
    SET department_group = 'PRODUCTION'
    WHERE department_code IN ('FIBER', 'YARN', 'WEAVING', 'KNITTING', 'DYEING', 'GARMENT');

UPDATE common_company.common_department
    SET department_group = 'SUPPORT'
    WHERE department_code IN ('HR', 'FINANCE', 'SALES', 'PROCUREMENT', 'QUALITY', 'WAREHOUSE');

-- 4. Deactivate removed umbrella departments
UPDATE common_company.common_department
    SET is_active = FALSE
    WHERE department_code IN ('PRODUCTION', 'MANAGEMENT');

-- 5. Clear stale parent_department_id references (PRODUCTION was parent of YARN, WEAVING, DYEING)
UPDATE common_company.common_department
    SET parent_department_id = NULL
    WHERE department_code IN ('YARN', 'WEAVING', 'DYEING', 'FIBER', 'KNITTING', 'GARMENT');

-- Procurement departmanındaki 'products' resource'unu 'procurement' olarak güncelle
UPDATE common_user.permission_template
SET resource = 'procurement', updated_at = NOW()
WHERE department_code = 'PROCUREMENT'
  AND resource = 'products'
  AND deleted_at IS NULL;

-- Procurement departmanındaki user-level override'ları da güncelle
UPDATE common_user.permission_override
SET resource = 'procurement', updated_at = NOW()
WHERE resource = 'products'
  AND user_id IN (
    SELECT DISTINCT u.id FROM common_user.common_user u
    JOIN common_user.common_user_department ud ON u.id = ud.user_id
    JOIN common_company.common_department d ON ud.department_id = d.id
    WHERE d.department_code = 'PROCUREMENT'
  )
  AND deleted_at IS NULL;

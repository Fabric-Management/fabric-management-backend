-- Add tenant type column
ALTER TABLE common_tenant.common_tenant 
ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'REGULAR';

-- Mark the existing Nexus Fabrics demo tenant as TEMPLATE if it exists
UPDATE common_tenant.common_tenant 
SET type = 'TEMPLATE' 
WHERE slug = 'nexus-fabrics';

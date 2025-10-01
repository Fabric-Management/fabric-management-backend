-- Remove user_contacts table
-- Contacts are now managed by Contact Service

-- Drop user_contacts table
DROP TABLE IF EXISTS user_contacts CASCADE;

-- Note: Contact information is now stored in Contact Service
-- Use ContactServiceClient to manage user contacts

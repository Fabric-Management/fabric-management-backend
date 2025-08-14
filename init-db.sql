-- Create user and database for user service
CREATE USER user_service WITH PASSWORD 'password';
CREATE DATABASE user_db OWNER user_service;

-- Grant all privileges
GRANT ALL PRIVILEGES ON DATABASE user_db TO user_service;
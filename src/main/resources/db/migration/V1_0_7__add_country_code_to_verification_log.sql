-- Add country_code column to common_verification_log for market-based routing
ALTER TABLE common_communication.common_verification_log 
ADD COLUMN IF NOT EXISTS country_code VARCHAR(10);

-- Create index for country_code queries
CREATE INDEX IF NOT EXISTS idx_vl_country_code 
ON common_communication.common_verification_log(country_code);

-- Add comment for documentation
COMMENT ON COLUMN common_communication.common_verification_log.country_code IS 
'ISO 3166-1 alpha-2 country code (e.g., TR, US, GB) extracted from phone number for market-based routing';

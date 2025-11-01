-- ============================================================================
-- V17: Address Validation Fields - Google Maps Platform Integration
-- ============================================================================
-- Adds validation and geolocation fields to common_address table
-- Supports Google Maps Platform integration for address standardization
-- Last Updated: 2025-11-01
-- ============================================================================

-- ============================================================================
-- ALTER TABLE: common_address
-- ============================================================================

-- Add country code (ISO 3166-1 alpha-2)
ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS country_code VARCHAR(2);

-- Add district/county
ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS district VARCHAR(100);

-- Add geolocation coordinates
ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;

ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

-- Add Google Places ID
ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS place_id VARCHAR(255);

-- Add formatted address (Google's canonical format)
ALTER TABLE common_communication.common_address
ADD COLUMN IF NOT EXISTS formatted_address VARCHAR(500);

-- Create indexes for geolocation queries
CREATE INDEX IF NOT EXISTS idx_address_country_code ON common_communication.common_address(country_code);
CREATE INDEX IF NOT EXISTS idx_address_place_id ON common_communication.common_address(place_id);
CREATE INDEX IF NOT EXISTS idx_address_coordinates ON common_communication.common_address(latitude, longitude) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

COMMENT ON COLUMN common_communication.common_address.country_code IS 'ISO 3166-1 alpha-2 country code (e.g., TR, GB, DE, FR)';
COMMENT ON COLUMN common_communication.common_address.district IS 'District/County/Sub-administrative area';
COMMENT ON COLUMN common_communication.common_address.latitude IS 'Latitude coordinate from Google Geocoding';
COMMENT ON COLUMN common_communication.common_address.longitude IS 'Longitude coordinate from Google Geocoding';
COMMENT ON COLUMN common_communication.common_address.place_id IS 'Google Places ID for address validation and revalidation';
COMMENT ON COLUMN common_communication.common_address.formatted_address IS 'Google formatted address (canonical format)';


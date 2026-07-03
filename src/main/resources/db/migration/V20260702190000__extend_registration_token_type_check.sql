-- Extend chk_registration_token_type to the full RegistrationTokenType enum.
-- V001 only allowed SALES_LED and SELF_SERVICE; INVITED_USER and
-- PARTNER_INVITED_USER inserts (regular/partner user invitations) violated the
-- check. The failure was masked for a long time because the invitation
-- listeners wrote the token in an AFTER_COMMIT phase without a new
-- transaction, so the insert was silently discarded (fixed alongside this
-- migration with REQUIRES_NEW on those listeners).

ALTER TABLE common_auth.common_registration_token
    DROP CONSTRAINT IF EXISTS chk_registration_token_type;

ALTER TABLE common_auth.common_registration_token
    ADD CONSTRAINT chk_registration_token_type
    CHECK (token_type IN ('SALES_LED', 'SELF_SERVICE', 'INVITED_USER', 'PARTNER_INVITED_USER'));

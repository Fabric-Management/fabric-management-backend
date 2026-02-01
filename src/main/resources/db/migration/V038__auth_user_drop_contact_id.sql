-- ============================================================================
-- V038: AuthUser - drop deprecated contact_id column
-- ============================================================================
-- Auth is user-based (one AuthUser per User). contact_id was deprecated.
-- DB empty per plan — drop column and index.
-- ============================================================================

DROP INDEX IF EXISTS common_auth.idx_auth_contact_id;
ALTER TABLE common_auth.common_auth_user DROP COLUMN IF EXISTS contact_id;

COMMENT ON TABLE common_auth.common_auth_user IS 'Authentication credentials per User (one AuthUser per User). contact_id removed.';

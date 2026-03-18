-- Rollback: V20260325093000__add_user_wip_limit.sql
ALTER TABLE common_user.common_user
    DROP COLUMN IF EXISTS wip_limit;

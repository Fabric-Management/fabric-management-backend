-- Rollback: V20260325090000__add_board_manager_user_id.sql
ALTER TABLE flowboard.board
    DROP COLUMN IF EXISTS manager_user_id;

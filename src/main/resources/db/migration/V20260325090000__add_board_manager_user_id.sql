-- ============================================================
-- [O4 FIX] Board.managerUserId
-- Escalation job'ının SystemUser.ID yerine board yöneticisine
-- bildirim gönderebilmesi için manager_user_id kolonu eklendi.
-- nullable: mevcut board kayıtları NULL olarak kalır.
-- Fallback: NULL ise SystemUser.ID kullanılır (kod seviyesinde).
-- ============================================================

ALTER TABLE flowboard.board
    ADD COLUMN IF NOT EXISTS manager_user_id UUID;

COMMENT ON COLUMN flowboard.board.manager_user_id
    IS 'Board''dan sorumlu yönetici UUID. NULL ise escalation SystemUser''a gider.';

-- ============================================================
-- TaskService içerisindeki TODO maddesini gidermek için User tablosuna
-- WIP (Work In Progress) limiti alanı eklendi.
-- ============================================================

ALTER TABLE common_user.common_user
    ADD COLUMN IF NOT EXISTS wip_limit INTEGER DEFAULT 5;

COMMENT ON COLUMN common_user.common_user.wip_limit
    IS 'Kullanıcının aynı anda IN_PROGRESS statüsünde barındırabileceği maksimum task limiti.';

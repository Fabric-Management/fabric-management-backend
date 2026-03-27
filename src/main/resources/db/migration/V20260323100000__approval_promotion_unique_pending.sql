-- Aynı kullanıcı için birden fazla PENDING terfi talebi oluşmasını engelleyen partial unique index.
-- Race condition savunması: @Async listener ile birden fazla thread aynı anda yazarsa DB seviyesinde engellenir.
CREATE UNIQUE INDEX IF NOT EXISTS uq_promotion_pending_per_user
    ON common_approval.user_promotion_request (tenant_id, user_id)
    WHERE status = 'PENDING' AND deleted_at IS NULL;

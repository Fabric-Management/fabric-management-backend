SET LOCAL lock_timeout = '5s';
CREATE INDEX IF NOT EXISTS idx_flowboard_order_cover_active
  ON flowboard.task(tenant_id,entity_id,created_at)
  WHERE task_type='ORDER_COVER' AND is_active AND closed_at IS NULL;

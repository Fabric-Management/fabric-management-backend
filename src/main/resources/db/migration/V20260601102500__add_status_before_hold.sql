ALTER TABLE sales_ord.sales_order
    ADD COLUMN status_before_hold VARCHAR(30);

-- Legacy ON_HOLD backfill: migration öncesi ON_HOLD'a alınmış siparişlerin
-- status_before_hold'u null olur → resume() çağrısında 500 (IllegalStateException).
-- Güvenli fallback: CONFIRMED (en yaygın hold-öncesi durum).
UPDATE sales_ord.sales_order
   SET status_before_hold = 'CONFIRMED'
 WHERE status = 'ON_HOLD'
   AND status_before_hold IS NULL;

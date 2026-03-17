-- Ensure work_order_number is truly unique at DB level (collision protection).
-- The service generates an 8-char random suffix (~4.3B possibilities) but this index
-- is the final safety net for concurrent inserts under extreme load.
ALTER TABLE production.prod_work_order
    ADD CONSTRAINT uq_work_order_number UNIQUE (work_order_number);

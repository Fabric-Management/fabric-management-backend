-- FAB-1025: Add certification and origin requirements to WorkOrder
ALTER TABLE production.prod_work_order
    ADD COLUMN certification_req VARCHAR(50),
    ADD COLUMN origin_req VARCHAR(10);

COMMENT ON COLUMN production.prod_work_order.certification_req IS
    'Customer-required certification standard (e.g. GOTS, OEKO-TEX, BCI). Normalized: UPPER, TRIM, blank→NULL.';
COMMENT ON COLUMN production.prod_work_order.origin_req IS
    'Customer-required fiber origin country code (e.g. TR, US, EG). Normalized: UPPER, TRIM, blank→NULL.';

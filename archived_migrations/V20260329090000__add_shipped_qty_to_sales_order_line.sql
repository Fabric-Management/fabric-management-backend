-- Migration to add shipped quantity and idempotency set to Sales Order Line in 'order' schema

ALTER TABLE "order".sales_order_line
    ADD COLUMN shipped_qty NUMERIC(15, 3) NOT NULL DEFAULT 0.000;

CREATE TABLE IF NOT EXISTS "order".sales_order_line_processed_shipments (
    sales_order_line_id UUID NOT NULL,
    shipment_line_id UUID NOT NULL,
    CONSTRAINT pk_sales_order_line_processed_shipments PRIMARY KEY (sales_order_line_id, shipment_line_id),
    CONSTRAINT fk_sol_processed_shipments_sol FOREIGN KEY (sales_order_line_id) REFERENCES "order".sales_order_line (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sol_processed_shipments_shipment_line_id ON "order".sales_order_line_processed_shipments (shipment_line_id);

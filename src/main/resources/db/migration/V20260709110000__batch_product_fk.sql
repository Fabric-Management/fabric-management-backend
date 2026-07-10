-- BATCH-FK-1
--
-- production_execution_batch.product_id has always been written with a prod_product.id
-- (every caller passes ProductDto.getId()), but fk_exec_batch_product pointed at
-- production.prod_fiber(id). prod_fiber.id and prod_product.id are independent id spaces:
-- prod_fiber links to its product through prod_fiber.product_id, not through a shared key.
--
-- The constraint was validated and enforced, so every batch insert violated it. The failure
-- was swallowed by a catch-and-warn in DemoTransactionSeeder, which is why the table is empty
-- rather than the application being loudly broken. A FABRIC or YARN batch could never have
-- satisfied this FK at all - those products have no prod_fiber row by definition.
--
-- Repoint the FK at prod_product. Fail loudly if any row disagrees.

DO $$
DECLARE
    unresolved BIGINT;
BEGIN
    SELECT count(*)
      INTO unresolved
      FROM production.production_execution_batch b
     WHERE NOT EXISTS (
             SELECT 1 FROM production.prod_product p WHERE p.id = b.product_id
           );

    IF unresolved > 0 THEN
        RAISE EXCEPTION
            'BATCH-FK-1: % batch row(s) have a product_id that is not a prod_product.id. '
            'Reconcile them before repointing the foreign key.', unresolved;
    END IF;
END $$;

ALTER TABLE production.production_execution_batch
    DROP CONSTRAINT IF EXISTS fk_exec_batch_product;

ALTER TABLE production.production_execution_batch
    ADD CONSTRAINT fk_exec_batch_product
        FOREIGN KEY (product_id)
        REFERENCES production.prod_product(id)
        ON DELETE RESTRICT;

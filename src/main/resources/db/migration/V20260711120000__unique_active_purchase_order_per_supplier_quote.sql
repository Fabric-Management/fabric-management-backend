DO $$
DECLARE
    duplicate_pairs TEXT;
BEGIN
    SELECT string_agg(
               format(
                   '(tenant_id=%s, supplier_quote_id=%s, count=%s)',
                   tenant_id,
                   supplier_quote_id,
                   duplicate_count),
               E'\n'
           )
      INTO duplicate_pairs
      FROM (
          SELECT tenant_id, supplier_quote_id, count(*) AS duplicate_count
            FROM procurement.purchase_order
           WHERE is_active = true
             AND supplier_quote_id IS NOT NULL
           GROUP BY tenant_id, supplier_quote_id
          HAVING count(*) > 1
           ORDER BY tenant_id, supplier_quote_id
      ) duplicates;

    IF duplicate_pairs IS NOT NULL THEN
        RAISE EXCEPTION
            'Cannot create uq_purchase_order_tenant_supplier_quote_active; duplicate active purchase orders:%',
            E'\n' || duplicate_pairs;
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_purchase_order_tenant_supplier_quote_active
    ON procurement.purchase_order (tenant_id, supplier_quote_id)
    WHERE is_active = true AND supplier_quote_id IS NOT NULL;

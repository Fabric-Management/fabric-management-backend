ALTER TABLE sales.quote_line
    ADD COLUMN fulfillment_mode varchar(32),
    ADD COLUMN fulfillment_determination_status varchar(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN fulfillment_determination_method varchar(16);

ALTER TABLE sales.quote_line
    ADD CONSTRAINT chk_quote_line_fulfillment_mode
        CHECK (
            fulfillment_mode IS NULL
            OR fulfillment_mode IN (
                'STOCK',
                'MAKE_TO_ORDER',
                'PURCHASE_TO_ORDER',
                'STOCK_AND_PRODUCTION'
            )
        )
        NOT VALID,
    ADD CONSTRAINT chk_quote_line_fulfillment_status
        CHECK (
            fulfillment_determination_status IN (
                'PENDING',
                'PROPOSED',
                'CONFIRMED',
                'OVERRIDDEN'
            )
        )
        NOT VALID,
    ADD CONSTRAINT chk_quote_line_fulfillment_method
        CHECK (
            fulfillment_determination_method IS NULL
            OR fulfillment_determination_method IN (
                'AUTOMATIC',
                'MANUAL',
                'POLICY_BASED'
            )
        )
        NOT VALID,
    ADD CONSTRAINT chk_quote_line_fulfillment_mode_status
        CHECK (
            (fulfillment_mode IS NULL)
            = (fulfillment_determination_status = 'PENDING')
        )
        NOT VALID,
    ADD CONSTRAINT chk_quote_line_fulfillment_mode_method
        CHECK (
            (fulfillment_determination_method IS NULL)
            = (fulfillment_mode IS NULL)
        )
        NOT VALID;

ALTER TABLE sales.quote_line
    VALIDATE CONSTRAINT chk_quote_line_fulfillment_mode,
    VALIDATE CONSTRAINT chk_quote_line_fulfillment_status,
    VALIDATE CONSTRAINT chk_quote_line_fulfillment_method,
    VALIDATE CONSTRAINT chk_quote_line_fulfillment_mode_status,
    VALIDATE CONSTRAINT chk_quote_line_fulfillment_mode_method;

-- Configuration chooses a reusable customer/scenario template. A real checkout
-- attempt must never reuse that template's business application number, because
-- transactions, customer support, reconciliation, and provider client fields
-- all use the application number as a business correlation key.
ALTER TABLE application_record
    ADD COLUMN checkout_template INTEGER NOT NULL DEFAULT 0
        CHECK (checkout_template IN (0, 1));

-- Releases before V11 stored one scenario application per synthetic customer.
-- Preserve the oldest of those rows as the configuration template. Subsequent
-- checkout-created rows retain the default value of zero and therefore never
-- pollute the predefined-customer selector.
UPDATE application_record
SET checkout_template = 1
WHERE id IN (
    SELECT MIN(id)
    FROM application_record
    GROUP BY customer_id
);

CREATE INDEX ix_application_checkout_template
    ON application_record(checkout_template, customer_id);

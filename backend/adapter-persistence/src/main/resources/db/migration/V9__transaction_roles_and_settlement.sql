-- Make mixed payment journeys explicit in the operator ledger.
--
-- A Koza Furikae checkout is not one financial transaction: it registers a
-- reusable mandate and then creates a separate Furikomi request for the first
-- premium.  Later monthly debits are separate MIT transactions.  The role and
-- settled amount columns let the read model explain those facts without
-- overloading method codes or provider statuses.

ALTER TABLE payment_transaction
    ADD COLUMN transaction_role TEXT NOT NULL DEFAULT 'PAYMENT';

ALTER TABLE payment_transaction
    ADD COLUMN settled_amount_jpy INTEGER NOT NULL DEFAULT 0 CHECK (settled_amount_jpy >= 0);

UPDATE payment_transaction
SET transaction_role = CASE
    WHEN operation IN ('REGISTER_AND_FIRST_TRANSFER', 'REGISTER_MANDATE_AND_ISSUE_FIRST_PAYMENT')
        THEN 'MANDATE_REGISTRATION'
    WHEN operation = 'SCHEDULE_DEBIT' THEN 'RECURRING_DEBIT'
    ELSE 'PAYMENT'
END;

UPDATE payment_transaction
SET settled_amount_jpy = amount_jpy
WHERE canonical_state IN ('PAID', 'CAPTURED', 'SALES');

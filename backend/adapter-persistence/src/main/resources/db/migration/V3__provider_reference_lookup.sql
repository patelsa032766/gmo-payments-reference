-- Browser returns and later provider notifications may refer to an earlier
-- registration ID after the transaction's primary access ID has advanced to a
-- debit or cash transaction. Resources preserve every provider reference.
CREATE INDEX ix_payment_resource_provider_reference
    ON payment_resource(provider_reference, transaction_id);

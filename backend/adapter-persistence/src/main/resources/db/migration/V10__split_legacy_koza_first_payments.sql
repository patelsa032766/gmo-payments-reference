-- Preserve already-created sandbox journeys under the clearer V9 model.
-- Earlier releases placed the first-premium Furikomi provider identifiers and
-- cash status on the Koza mandate row.  Only conclusive instruction/payment
-- rows can be split safely; failed or still-pending registrations stay intact.

INSERT INTO payment_transaction
    (transaction_id, root_transaction_id, application_id, customer_id, instrument_id,
     method_code, product_code, initiation_type, operation, transaction_role,
     amount_jpy, settled_amount_jpy, currency, canonical_state, merchant_reference,
     provider_order_id, provider_access_id, provider_status, requires_attention,
     configuration_version, created_at, updated_at)
SELECT 'TXN-FURIKOMI-LEGACY-' || printf('%08X', t.id), t.transaction_id,
       t.application_id, t.customer_id, t.instrument_id,
       'furikomi', 'bank_transfer_gmo_aozora', 'CIT', 'FIRST_PREMIUM_TRANSFER',
       'FIRST_PREMIUM', t.amount_jpy, t.settled_amount_jpy, t.currency,
       t.canonical_state, COALESCE(t.provider_order_id, t.merchant_reference),
       t.provider_order_id, t.provider_access_id, t.provider_status,
       t.requires_attention, t.configuration_version, t.created_at, t.updated_at
FROM payment_transaction t
WHERE t.method_code='kozaFurikae'
  AND t.transaction_role='MANDATE_REGISTRATION'
  AND t.canonical_state IN ('INSTRUCTIONS_ISSUED', 'PARTIALLY_PAID', 'PAID')
  AND NOT EXISTS (SELECT 1 FROM payment_transaction child
                  WHERE child.root_transaction_id=t.transaction_id
                    AND child.transaction_role='FIRST_PREMIUM');

INSERT INTO payment_event
    (event_id, transaction_id, event_type, source, summary,
     canonical_state_after, actor, correlation_id, evidence_json, occurred_at)
SELECT 'EVT-MIG-FP-' || printf('%08X', child.id), child.id,
       'FIRST_PAYMENT_SEPARATED', 'MIGRATION',
       'Legacy first-premium Furikomi separated from Koza mandate',
       child.canonical_state, 'system',
       COALESCE((SELECT e.correlation_id FROM payment_event e
                 JOIN payment_transaction parent ON parent.id=e.transaction_id
                 WHERE parent.transaction_id=child.root_transaction_id
                 ORDER BY e.id LIMIT 1), 'MIGRATION'), '{}', child.updated_at
FROM payment_transaction child
WHERE child.transaction_id LIKE 'TXN-FURIKOMI-LEGACY-%'
  AND NOT EXISTS (SELECT 1 FROM payment_event e
                  WHERE e.transaction_id=child.id AND e.event_type='FIRST_PAYMENT_SEPARATED');

UPDATE payment_transaction
SET amount_jpy=0, settled_amount_jpy=0, canonical_state='MANDATE_REGISTERED',
    provider_status='SUCCESS', requires_attention=0,
    provider_order_id=NULL, provider_access_id=NULL, version=version+1
WHERE method_code='kozaFurikae'
  AND transaction_role='MANDATE_REGISTRATION'
  AND EXISTS (SELECT 1 FROM payment_transaction child
              WHERE child.root_transaction_id=payment_transaction.transaction_id
                AND child.transaction_role='FIRST_PREMIUM');


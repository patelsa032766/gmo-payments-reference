-- Earlier builds used the authorization-and-store label for both AUTH and
-- CAPTURE card charges. Normalize only conclusive immediate-sale rows so the
-- operator timeline describes the GMO outcome accurately. Authorization-only
-- history remains untouched.
UPDATE payment_event
SET event_type = 'PAYMENT_CAPTURED',
    summary = 'Card payment captured and saved for recurring payments'
WHERE event_type = 'CARD_AUTHORIZED_AND_STORED'
  AND transaction_id IN (
      SELECT id
      FROM payment_transaction
      WHERE method_code = 'card'
        AND operation = 'SALE'
        AND canonical_state = 'PAID'
  );

-- Seed instruments demonstrate the operator UI in simulation mode but do not
-- exist in a merchant's GMO account. Mark them explicitly so a live frontend
-- can omit them and the provider adapter can reject direct API misuse before a
-- GMO financial request is attempted.
UPDATE payment_instrument
SET metadata_json = json_set(metadata_json, '$.prototype', json('true'))
WHERE instrument_id IN (
    'PM-CARD-10042-01',
    'PM-PAYPAY-10042-01',
    'PM-BANK-10043-01',
    'PM-KOZA-10046-01'
);

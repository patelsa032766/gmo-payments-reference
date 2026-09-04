-- Execution intent is release-owned for customer-initiated payments. Only
-- products that support both authorization and immediate sale use AUTH; other
-- products retain CAPTURE as a harmless, normalized default.
ALTER TABLE payment_method_configuration
    ADD COLUMN cit_execution_mode TEXT NOT NULL DEFAULT 'CAPTURE'
    CHECK (cit_execution_mode IN ('AUTH', 'CAPTURE'));

UPDATE payment_method_configuration
SET cit_execution_mode = 'AUTH'
WHERE code IN ('card', 'paypay');

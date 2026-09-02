-- Durable payment operations schema.
--
-- Financial state is projected onto payment_transaction for fast operator
-- reads, while payment_event and provider_exchange preserve the immutable
-- evidence used to explain how that projection was reached. Provider payloads
-- stored in these tables MUST be sanitized before this persistence boundary.

CREATE TABLE customer (
    id INTEGER PRIMARY KEY,
    customer_code TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    ekyc_verified INTEGER NOT NULL DEFAULT 0 CHECK (ekyc_verified IN (0, 1)),
    identity_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE application_record (
    id INTEGER PRIMARY KEY,
    application_number TEXT NOT NULL UNIQUE,
    customer_id INTEGER NOT NULL REFERENCES customer(id),
    policy_name TEXT NOT NULL,
    distribution_channel TEXT NOT NULL CHECK (distribution_channel IN ('PA', 'IA', 'FI')),
    payment_plan TEXT NOT NULL CHECK (payment_plan IN ('ONE_TIME', 'MONTHLY')),
    amount_jpy INTEGER NOT NULL CHECK (amount_jpy > 0),
    currency TEXT NOT NULL DEFAULT 'JPY' CHECK (currency = 'JPY'),
    selected_method TEXT,
    state TEXT NOT NULL DEFAULT 'DRAFT',
    configuration_version INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE payment_instrument (
    id INTEGER PRIMARY KEY,
    instrument_id TEXT NOT NULL UNIQUE,
    customer_id INTEGER NOT NULL REFERENCES customer(id),
    method_code TEXT NOT NULL,
    product_code TEXT NOT NULL,
    provider_member_reference TEXT,
    provider_instrument_reference TEXT,
    masked_display TEXT NOT NULL,
    state TEXT NOT NULL,
    preference_role TEXT CHECK (preference_role IN ('PRIMARY', 'BACKUP') OR preference_role IS NULL),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    version INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX ux_instrument_customer_primary
    ON payment_instrument(customer_id) WHERE preference_role = 'PRIMARY' AND state = 'ACTIVE';
CREATE UNIQUE INDEX ux_instrument_customer_backup
    ON payment_instrument(customer_id) WHERE preference_role = 'BACKUP' AND state = 'ACTIVE';

CREATE TABLE payment_transaction (
    id INTEGER PRIMARY KEY,
    transaction_id TEXT NOT NULL UNIQUE,
    root_transaction_id TEXT REFERENCES payment_transaction(transaction_id),
    application_id INTEGER REFERENCES application_record(id),
    customer_id INTEGER NOT NULL REFERENCES customer(id),
    instrument_id INTEGER REFERENCES payment_instrument(id),
    method_code TEXT NOT NULL,
    product_code TEXT NOT NULL,
    initiation_type TEXT NOT NULL CHECK (initiation_type IN ('CIT', 'MIT')),
    operation TEXT NOT NULL,
    amount_jpy INTEGER NOT NULL CHECK (amount_jpy >= 0),
    currency TEXT NOT NULL DEFAULT 'JPY' CHECK (currency = 'JPY'),
    canonical_state TEXT NOT NULL,
    merchant_reference TEXT NOT NULL,
    provider_order_id TEXT,
    provider_access_id TEXT,
    provider_status TEXT,
    requires_attention INTEGER NOT NULL DEFAULT 0 CHECK (requires_attention IN (0, 1)),
    configuration_version INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX ix_transaction_application ON payment_transaction(application_id);
CREATE INDEX ix_transaction_customer ON payment_transaction(customer_id);
CREATE INDEX ix_transaction_provider_order ON payment_transaction(provider_order_id);
CREATE INDEX ix_transaction_root ON payment_transaction(root_transaction_id);

CREATE TABLE payment_resource (
    id INTEGER PRIMARY KEY,
    resource_id TEXT NOT NULL UNIQUE,
    transaction_id INTEGER NOT NULL REFERENCES payment_transaction(id),
    resource_type TEXT NOT NULL,
    provider_reference TEXT,
    amount_jpy INTEGER,
    state TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE payment_event (
    id INTEGER PRIMARY KEY,
    event_id TEXT NOT NULL UNIQUE,
    transaction_id INTEGER NOT NULL REFERENCES payment_transaction(id),
    event_type TEXT NOT NULL,
    source TEXT NOT NULL,
    summary TEXT NOT NULL,
    canonical_state_after TEXT,
    actor TEXT,
    correlation_id TEXT NOT NULL,
    evidence_json TEXT NOT NULL DEFAULT '{}',
    provider_occurred_at TEXT,
    occurred_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX ix_event_transaction_time ON payment_event(transaction_id, occurred_at, id);

CREATE TABLE provider_exchange (
    id INTEGER PRIMARY KEY,
    exchange_id TEXT NOT NULL UNIQUE,
    transaction_id INTEGER REFERENCES payment_transaction(id),
    event_id INTEGER REFERENCES payment_event(id),
    direction TEXT NOT NULL CHECK (direction IN ('OUTBOUND', 'INBOUND', 'PAIRED')),
    transport TEXT NOT NULL CHECK (transport IN ('OPENAPI', 'IDPASS', 'WEBHOOK', 'SFTP', 'INQUIRY', 'BROWSER_RETURN')),
    operation TEXT NOT NULL,
    endpoint TEXT,
    http_status INTEGER,
    duration_ms INTEGER,
    request_headers_json TEXT NOT NULL DEFAULT '{}',
    request_body_json TEXT NOT NULL DEFAULT '{}',
    response_headers_json TEXT NOT NULL DEFAULT '{}',
    response_body_json TEXT NOT NULL DEFAULT '{}',
    outcome TEXT NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    correlation_id TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE inbound_message (
    id INTEGER PRIMARY KEY,
    message_id TEXT NOT NULL UNIQUE,
    source_family TEXT NOT NULL,
    external_event_key TEXT,
    payload_hash TEXT NOT NULL UNIQUE,
    transaction_id INTEGER REFERENCES payment_transaction(id),
    sanitized_payload TEXT NOT NULL,
    parse_status TEXT NOT NULL,
    acknowledgement_status TEXT NOT NULL,
    linkage_status TEXT NOT NULL,
    received_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    processed_at TEXT
);

CREATE TABLE idempotency_record (
    id INTEGER PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    command_type TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    status TEXT NOT NULL,
    transaction_id INTEGER REFERENCES payment_transaction(id),
    response_json TEXT NOT NULL DEFAULT '{}',
    expires_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE debit_batch (
    id INTEGER PRIMARY KEY,
    batch_id TEXT NOT NULL UNIQUE,
    batch_reference TEXT NOT NULL UNIQUE,
    cycle_year INTEGER NOT NULL,
    cycle_month INTEGER NOT NULL CHECK (cycle_month BETWEEN 1 AND 12),
    target_date TEXT NOT NULL,
    submission_cutoff_at TEXT NOT NULL,
    expected_result_date TEXT NOT NULL,
    state TEXT NOT NULL,
    selected_count INTEGER NOT NULL DEFAULT 0,
    selected_total_jpy INTEGER NOT NULL DEFAULT 0,
    created_by TEXT NOT NULL,
    submitted_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE debit_batch_item (
    id INTEGER PRIMARY KEY,
    batch_id INTEGER NOT NULL REFERENCES debit_batch(id),
    transaction_id INTEGER NOT NULL UNIQUE REFERENCES payment_transaction(id),
    instrument_id INTEGER NOT NULL REFERENCES payment_instrument(id),
    amount_jpy INTEGER NOT NULL CHECK (amount_jpy > 0),
    state TEXT NOT NULL,
    result_code TEXT,
    failure_reason TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    UNIQUE (batch_id, instrument_id)
);

CREATE TABLE reconciliation_file (
    id INTEGER PRIMARY KEY,
    file_id TEXT NOT NULL UNIQUE,
    remote_name TEXT NOT NULL,
    checksum_sha256 TEXT NOT NULL UNIQUE,
    ready_marker_name TEXT,
    source TEXT NOT NULL,
    state TEXT NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    imported_by TEXT NOT NULL,
    received_at TEXT NOT NULL,
    completed_at TEXT
);

CREATE TABLE reconciliation_row (
    id INTEGER PRIMARY KEY,
    file_id INTEGER NOT NULL REFERENCES reconciliation_file(id),
    row_number INTEGER NOT NULL,
    row_hash TEXT NOT NULL,
    provider_order_id TEXT,
    provider_status TEXT,
    amount_jpy INTEGER,
    event_occurred_at TEXT,
    sanitized_row_json TEXT NOT NULL,
    parse_status TEXT NOT NULL,
    rejection_reason TEXT,
    UNIQUE (file_id, row_number),
    UNIQUE (file_id, row_hash)
);

CREATE TABLE reconciliation_match (
    id INTEGER PRIMARY KEY,
    row_id INTEGER NOT NULL UNIQUE REFERENCES reconciliation_row(id),
    transaction_id INTEGER REFERENCES payment_transaction(id),
    match_status TEXT NOT NULL,
    resolution TEXT,
    resolved_by TEXT,
    resolved_at TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE job_attempt (
    id INTEGER PRIMARY KEY,
    job_id TEXT NOT NULL,
    job_type TEXT NOT NULL,
    subject_id TEXT NOT NULL,
    attempt_number INTEGER NOT NULL,
    state TEXT NOT NULL,
    next_attempt_at TEXT,
    lease_owner TEXT,
    lease_expires_at TEXT,
    terminal_reason TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    UNIQUE (job_id, attempt_number)
);

CREATE TABLE system_feature_configuration (
    release_id INTEGER NOT NULL REFERENCES configuration_release(id),
    feature_code TEXT NOT NULL,
    enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),
    value_json TEXT NOT NULL DEFAULT '{}',
    PRIMARY KEY (release_id, feature_code)
);

CREATE TABLE retry_policy_configuration (
    release_id INTEGER NOT NULL REFERENCES configuration_release(id),
    operation_code TEXT NOT NULL,
    maximum_attempts INTEGER NOT NULL CHECK (maximum_attempts BETWEEN 1 AND 10),
    base_delay_ms INTEGER NOT NULL CHECK (base_delay_ms >= 0),
    maximum_delay_ms INTEGER NOT NULL CHECK (maximum_delay_ms >= base_delay_ms),
    jitter_ratio REAL NOT NULL CHECK (jitter_ratio BETWEEN 0 AND 1),
    retryable_codes TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (release_id, operation_code)
);

INSERT INTO system_feature_configuration (release_id, feature_code, enabled, value_json) VALUES
    (1, 'gmo_live_calls', 0, '{"mode":"SIMULATED"}'),
    (1, 'webhooks', 0, '{"openapiPath":"/webhooks/gmo/openapi","protocolPath":"/webhooks/gmo/protocol"}'),
    (1, 'sftp_reconciliation', 0, '{"readyMarkerSuffix":".ok"}'),
    (1, 'koza_furikae_batches', 1, '{"calendarSource":"CONFIGURED"}'),
    (1, 'koza_async_notifications', 1, '{}');

INSERT INTO retry_policy_configuration
    (release_id, operation_code, maximum_attempts, base_delay_ms, maximum_delay_ms, jitter_ratio, retryable_codes)
VALUES
    (1, 'provider_safe_read', 4, 250, 4000, 0.20, 'HTTP_429,HTTP_502,HTTP_503,HTTP_504,CONNECT_TIMEOUT'),
    (1, 'provider_financial_write', 1, 0, 0, 0.00, ''),
    (1, 'sqlite_busy', 5, 25, 400, 0.25, 'SQLITE_BUSY,SQLITE_LOCKED'),
    (1, 'sftp_transfer', 4, 1000, 30000, 0.20, 'CONNECT_TIMEOUT,CHANNEL_CLOSED');

-- Synthetic operator fixtures reproduce the approved mock on a clean clone.
-- They are unmistakably fake and contain no imported Flask/GMO customer data.
INSERT INTO customer (id, customer_code, full_name, ekyc_verified, identity_hash) VALUES
    (1, 'CUST-10042', 'Aiko Tanaka', 1, 'synthetic:aiko-tanaka'),
    (2, 'CUST-10043', 'Haruto Sato', 1, 'synthetic:haruto-sato'),
    (3, 'CUST-10044', 'Yuina Nakamura', 0, 'synthetic:yuina-nakamura'),
    (4, 'CUST-10045', 'Ken Ito', 1, 'synthetic:ken-ito'),
    (5, 'CUST-10046', 'Emi Watanabe', 1, 'synthetic:emi-watanabe');

INSERT INTO application_record
    (id, application_number, customer_id, policy_name, distribution_channel, payment_plan,
     amount_jpy, selected_method, state, configuration_version)
VALUES
    (1, 'APP-20260821-001', 1, 'Annuity', 'PA', 'MONTHLY', 10000, 'card', 'AUTHORIZED', 1),
    (2, 'APP-20260829-018', 2, 'Annuity', 'PA', 'MONTHLY', 10000, 'bankDirect', 'PAID', 1),
    (3, 'APP-20260829-022', 3, 'Annuity', 'PA', 'ONE_TIME', 7500, 'kombini', 'INSTRUCTIONS_ISSUED', 1),
    (4, 'APP-20260828-009', 5, 'Annuity', 'FI', 'ONE_TIME', 85000, 'furikomi', 'PAID', 1);

INSERT INTO payment_instrument
    (id, instrument_id, customer_id, method_code, product_code, provider_member_reference,
     provider_instrument_reference, masked_display, state, preference_role, metadata_json)
VALUES
    (1, 'PM-CARD-10042-01', 1, 'card', 'card_on_file', 'GMO-MEMBER-10042', 'CARD-042', 'Visa •••• 4242', 'ACTIVE', 'BACKUP', '{"expiry":"12/29"}'),
    (2, 'PM-PAYPAY-10042-01', 1, 'paypay', 'paypay_recurring', 'GMO-MEMBER-10042', 'PAYPAY-042', 'PayPay recurring', 'ACTIVE', 'PRIMARY', '{}'),
    (3, 'PM-BANK-10043-01', 2, 'bankDirect', 'bank_direct_realtime', 'GMO-MEMBER-10043', 'BANK-043', 'Bank account •••• 0422', 'ACTIVE', 'PRIMARY', '{}'),
    (4, 'PM-KOZA-10046-01', 5, 'kozaFurikae', 'koza_furikae_select', 'GMO-MEMBER-10046', 'KZA-046', 'Koza Furikae mandate •••• 9046', 'ACTIVE', 'PRIMARY', '{"registrationStatus":"SUCCESS"}');

INSERT INTO payment_transaction
    (id, transaction_id, application_id, customer_id, instrument_id, method_code, product_code,
     initiation_type, operation, amount_jpy, canonical_state, merchant_reference,
     provider_order_id, provider_access_id, provider_status, requires_attention, configuration_version,
     created_at, updated_at)
VALUES
    (1, 'TXN-CARD-13919584', 1, 1, 1, 'card', 'card_openapi', 'CIT', 'AUTHORIZE', 10000, 'AUTHORIZED', 'APP-20260821-001', 'APP-20260821-001', 'synthetic-access-card', 'AUTH', 0, 1, '2026-08-31T02:18:00Z', '2026-08-31T02:18:00Z'),
    (2, 'TXN-PP-41892105', NULL, 1, 2, 'paypay', 'paypay_recurring', 'MIT', 'CAPTURE', 12500, 'PARTIALLY_REFUNDED', 'MIT-20260830-004', 'MIT-20260830-004', 'synthetic-access-paypay', 'SALES', 0, 1, '2026-08-31T03:24:00Z', '2026-08-31T03:24:00Z'),
    (3, 'TXN-DD-77180422', 2, 2, 3, 'bankDirect', 'bank_direct_realtime', 'CIT', 'IMMEDIATE_DEBIT', 10000, 'PAID', 'APP-20260829-018', 'APP-20260829-018', 'synthetic-access-bank', 'PAYSUCCESS', 0, 1, '2026-08-30T11:51:00Z', '2026-08-30T11:51:00Z'),
    (4, 'TXN-KB-22041003', 3, 3, NULL, 'kombini', 'kombini_openapi', 'CIT', 'ISSUE_INSTRUCTIONS', 7500, 'INSTRUCTIONS_ISSUED', 'APP-20260829-022', 'APP-20260829-022', 'synthetic-access-kombini', 'REQSUCCESS', 0, 1, '2026-08-30T08:42:00Z', '2026-08-30T08:42:00Z'),
    (5, 'TXN-CARD-88172064', NULL, 4, NULL, 'card', 'card_on_file', 'MIT', 'CAPTURE', 25000, 'RETRY_REQUIRED', 'MIT-20260830-003', 'MIT-20260830-003', NULL, 'UNKNOWN', 1, 1, '2026-08-31T00:32:00Z', '2026-08-31T00:32:00Z'),
    (6, 'TXN-BT-51809271', 4, 5, NULL, 'furikomi', 'furikomi_virtual_account', 'CIT', 'ISSUE_INSTRUCTIONS', 85000, 'PAID', 'APP-20260828-009', 'APP-20260828-009', 'synthetic-access-furikomi', 'PAYSUCCESS', 0, 1, '2026-08-30T10:05:00Z', '2026-08-30T10:05:00Z');

INSERT INTO payment_event
    (event_id, transaction_id, event_type, source, summary, canonical_state_after, actor,
     correlation_id, evidence_json, occurred_at)
VALUES
    ('EVT-0001', 1, 'PAYMENT_AUTHORIZED', 'GMO_API', 'Card authorization accepted', 'AUTHORIZED', 'system', 'CORR-0001', '{}', '2026-08-31T02:18:00Z'),
    ('EVT-0002', 2, 'PAYMENT_CAPTURED', 'GMO_API', 'PayPay payment captured', 'PAID', 'system', 'CORR-0002', '{}', '2026-08-31T03:20:00Z'),
    ('EVT-0003', 2, 'REFUND_COMPLETED', 'GMO_WEBHOOK', 'JPY 2,500 refunded', 'PARTIALLY_REFUNDED', 'system', 'CORR-0003', '{"amountJpy":2500}', '2026-08-31T03:24:00Z'),
    ('EVT-0004', 3, 'PAYMENT_COMPLETED', 'GMO_PROTOCOL', 'Real-time bank debit completed', 'PAID', 'system', 'CORR-0004', '{}', '2026-08-30T11:51:00Z'),
    ('EVT-0005', 4, 'INSTRUCTIONS_ISSUED', 'GMO_API', 'Convenience-store instructions issued', 'INSTRUCTIONS_ISSUED', 'system', 'CORR-0005', '{}', '2026-08-30T08:42:00Z'),
    ('EVT-0006', 5, 'OUTCOME_UNKNOWN', 'GMO_API', 'Provider outcome requires inquiry', 'RETRY_REQUIRED', 'system', 'CORR-0006', '{}', '2026-08-31T00:32:00Z'),
    ('EVT-0007', 6, 'PAYMENT_COMPLETED', 'SFTP', 'Bank transfer matched by reconciliation', 'PAID', 'system', 'CORR-0007', '{}', '2026-08-30T10:05:00Z');

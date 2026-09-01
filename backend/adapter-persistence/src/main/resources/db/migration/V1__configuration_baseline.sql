-- Connection-level PRAGMAs belong in the JDBC URL, not in a migration.
-- Mixing PRAGMA statements with transactional DDL would make Flyway reject
-- this migration and would not reliably configure later pooled connections.

CREATE TABLE configuration_release (
    id INTEGER PRIMARY KEY,
    version INTEGER NOT NULL UNIQUE,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    published_at TEXT,
    published_by TEXT,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE UNIQUE INDEX ux_configuration_one_published
    ON configuration_release(status)
    WHERE status = 'PUBLISHED';

CREATE TABLE payment_method_configuration (
    id INTEGER PRIMARY KEY,
    release_id INTEGER NOT NULL REFERENCES configuration_release(id),
    code TEXT NOT NULL,
    label_en TEXT NOT NULL,
    description_en TEXT NOT NULL,
    label_ja TEXT NOT NULL,
    description_ja TEXT NOT NULL,
    enabled INTEGER NOT NULL CHECK (enabled IN (0, 1)),
    recurring INTEGER NOT NULL CHECK (recurring IN (0, 1)),
    monthly_only INTEGER NOT NULL CHECK (monthly_only IN (0, 1)),
    min_amount_jpy INTEGER NOT NULL CHECK (min_amount_jpy >= 0),
    max_amount_jpy INTEGER NOT NULL CHECK (max_amount_jpy >= min_amount_jpy),
    non_ekyc_max_amount_jpy INTEGER,
    channels TEXT NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    UNIQUE (release_id, code),
    UNIQUE (release_id, display_order)
);

INSERT INTO configuration_release (id, version, status, published_at, published_by)
VALUES (1, 1, 'PUBLISHED', '2026-09-01T00:00:00Z', 'baseline-v28');

INSERT INTO payment_method_configuration
    (id, release_id, code, label_en, description_en, label_ja, description_ja,
     enabled, recurring, monthly_only, min_amount_jpy, max_amount_jpy,
     non_ekyc_max_amount_jpy, channels, display_order)
VALUES
    (1, 1, 'card', 'Credit or debit card', 'Visa, Mastercard, JCB, and American Express',
     'クレジットカード／デビットカード', 'Visa、Mastercard、JCB、American Express',
     1, 1, 0, 1, 1000000, NULL, 'PA,IA,FI', 1),
    (2, 1, 'paypay', 'PayPay', 'Pay from your PayPay balance or linked account',
     'PayPay', 'PayPay残高または連携口座からお支払い',
     1, 1, 0, 1, 500000, NULL, 'PA,IA', 2),
    (3, 1, 'bankDirect', 'Real-time bank debit', 'Register a supported bank account and debit today',
     '口座直結決済', '対応する銀行口座を登録して、本日すぐに引き落とし',
     1, 1, 0, 1, 300000, 50000, 'PA,IA,FI', 3),
    (4, 1, 'kozaFurikae', 'Bank transfer today + monthly bank debit',
     'Register monthly bank debit, then receive today''s bank-transfer instructions',
     '初回銀行振込＋口座振替', '今後の口座振替を登録し、初回保険料の銀行振込先を受け取る',
     1, 1, 1, 1, 1000000, NULL, 'PA,IA,FI', 4),
    (5, 1, 'kombini', 'Convenience store', 'Receive a receipt and pay at a supported store',
     'コンビニ払い', 'お支払い番号を受け取り、対応店舗でお支払い',
     1, 0, 0, 1, 299999, NULL, 'PA,IA,FI', 5),
    (6, 1, 'payeasy', 'Pay-easy', 'Pay through ATM or online banking using issued numbers',
     'ペイジー', '発行された番号を使ってATMまたはネットバンキングでお支払い',
     1, 0, 0, 1, 300000, NULL, 'PA,IA', 6),
    (7, 1, 'furikomi', 'Bank transfer', 'Transfer to a one-time virtual account',
     '銀行振込', '今回のお支払い専用の振込口座へ送金',
     1, 0, 0, 1, 1000000, NULL, 'PA,FI', 7);

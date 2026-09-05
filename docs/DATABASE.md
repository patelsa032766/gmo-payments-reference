# SQLite Data and Concurrency Guide

SQLite stores configuration, local payment state, sanitized API/webhook evidence, stored-instrument roles, Koza batches, and SFTP reconciliation checkpoints. It does not store raw PAN/CVC, bank login data, provider passwords, SFTP keys, ingress secrets, or tunnel credentials.

## Ownership and startup

Flyway is the only schema owner. Never edit an applied migration; add a new file under `backend/adapter-persistence/src/main/resources/db/migration` and test both a fresh database and an upgrade.

Default URL:

```text
jdbc:sqlite:runtime/gmo-payments.db?journal_mode=WAL&busy_timeout=5000&foreign_keys=on
```

With `scripts/run-backend.sh`, the file resolves to `backend/bootstrap/runtime/gmo-payments.db`. Deployments should use an absolute `DATABASE_URL` on durable private storage.

## Tables and interaction ownership

| Table | Purpose | Principal writer |
| --- | --- | --- |
| `configuration_release` | Immutable draft/published/retired versions | Configuration use case |
| `payment_method_configuration` | Eligibility, order, thresholds, channels, EN/JA copy, and Card/PayPay CIT execution policy | Configuration use case |
| `customer` | Local customer identity/reference | Checkout/MIT bootstrap |
| `application_record` | Policy/application, amount, plan, customer, chosen configuration version | Checkout |
| `checkout_experience_settings` | Singleton local-demo selection: application, language, and configuration-authentication flag | Configuration use case |
| `payment_instrument` | Masked provider reference, lifecycle, optimistic version, Primary/Backup role | Successful registration and preference command |
| `payment_transaction` | Root financial thread and current canonical projection | Checkout, MIT, batch orchestration |
| `payment_resource` | Authorization, capture, debit, transfer, refund, or dispute child reference | Provider result/inbound processing |
| `payment_event` | Append-only business and transport evidence | Every state-changing use case |
| `provider_exchange` | Sanitized outbound request and inbound response paired to an event | GMO adapter result persistence |
| `inbound_message` | Deduplicated webhook/protocol envelope and linkage state | Webhook receiver |
| `idempotency_record` | Command key, request fingerprint, execution status, linked result | Checkout/MIT/capture command reservation |
| `debit_batch` | Monthly Koza cycle/submission state | Koza batch use case |
| `debit_batch_item` | One Koza instrument and transaction per requested debit | Koza batch use case |
| `reconciliation_file` | Filename, checksum, import/archive state | SFTP importer |
| `reconciliation_row` | Sanitized parsed row and matching state | Reconciliation parser |
| `reconciliation_match` | Agreement/discrepancy linked to a transaction | Reconciliation matcher |
| `job_attempt` | Lease, attempt count, next attempt, terminal reason | Background workers |
| `system_feature_configuration` | Safe persisted feature/business flags | Configuration bootstrap |
| `retry_policy_configuration` | Named retry settings for controlled operations | Configuration bootstrap |

`V3__provider_reference_lookup.sql` indexes provider resource references so browser returns and asynchronous messages can find the original thread without a table scan.

## Transaction-thread model

`payment_transaction` is the root. Its `canonical_state` is a fast projection; `payment_event` is the ordered audit history. `provider_exchange` attaches the precise sanitized request/response pair to the event that interpreted it.

Later lifecycle activity is never detached:

```text
payment_transaction
  payment_event: submitted
    provider_exchange: authorization request/response
  payment_event: captured
    provider_exchange: capture request/response
  payment_event: refund requested
    provider_exchange: refund request/response
  payment_event: webhook received
  payment_event: chargeback reported
  payment_event: SFTP reconciliation matched
```

Provider IDs can appear on `payment_transaction` and `payment_resource`. All correlation paths resolve back to the same local root.

## Configuration read/write behavior

Checkout first resolves the single `PUBLISHED` release, then reads method rows using that fixed release ID. It cannot mix methods from two versions while publication occurs.

Editing creates or replaces one `DRAFT` release. Publishing runs as a short transaction that retires the previous published row and publishes the draft. Transactions retain their configuration version for later explanation. `cit_execution_mode` stores `AUTH` or `CAPTURE`; checkout resolves it from the application’s pinned release and overwrites any browser-supplied value.

The local test scenario is separate from immutable payment-method releases. Its
singleton row selects one predefined `application_record`, checkout language,
and whether configuration-page mutations require the shared development token.
Changing **Due today** updates the selected application amount in the same short
SQLite transaction. Financial operator APIs never consult this flag.

## Payment command interaction

The command pattern is deliberately split around network I/O:

1. Validate input and reserve an idempotency record plus local transaction/event in a short database transaction.
2. Commit.
3. Call GMO without holding a database lock.
4. Persist sanitized outcome, provider exchanges, resources, instrument changes, and idempotency completion in another short transaction.
5. If the provider result is ambiguous, retain `UNKNOWN`/attention evidence and use inquiry; do not repeat the financial write.

This makes process restart recoverable and avoids turning a slow provider into a SQLite write lock.

## Instrument role invariants

Partial unique indexes permit at most one active `PRIMARY` and one active `BACKUP` per customer. Application validation also ensures that Primary and Backup differ and belong to the selected customer. On successful reusable-method registration, the newest instrument becomes Primary, the previous Primary becomes Backup, and an older Backup is cleared.

These labels currently drive operator visibility and future scheduling decisions. Automatic fallback charging is intentionally not performed without an explicit payment-run policy.

## Lock handling

SQLite permits multiple readers but serializes writers. The implementation uses several layers rather than relying on one setting:

- WAL mode for concurrent readers during commits.
- `busy_timeout=5000` on every connection for short lock bursts.
- Foreign keys enabled on every connection.
- Hikari pool capped at four; a larger pool does not create more SQLite writers.
- Short application transactions with no network calls or sleeps inside them.
- Optimistic version columns for mutable projections.
- `SQLiteLockRetryExecutor` for bounded exponential backoff with jitter on recognized busy/locked failures.
- Idempotency records so a caller can safely retry a local command after lock exhaustion.
- Single controlled scheduler paths for SFTP/batch maintenance rather than concurrent fan-out writers.

After retry exhaustion, the operation fails visibly with a retryable service error. It is never silently discarded.

## Webhook and SFTP deduplication

Webhook bodies are sanitized and hashed. Re-delivery of the same payload returns the existing inbound record and does not append the lifecycle transition twice.

SFTP imports deduplicate at two levels:

- file checksum/identity prevents importing the same file twice;
- normalized row identity prevents double-applying repeated transaction results.

Unmatched or conflicting evidence remains stored for operator review. Reconciliation never overwrites a prior provider response merely to make sources agree.

## Inspection

For local debugging only:

```bash
sqlite3 backend/bootstrap/runtime/gmo-payments.db '.tables'
sqlite3 backend/bootstrap/runtime/gmo-payments.db \
  'select transaction_id, canonical_state, updated_at from payment_transaction order by updated_at desc limit 10;'
```

Do not query or copy a production database into a public issue or repository. Operator screens are the preferred sanitized inspection path.

## Backup, restore, and WAL maintenance

Use SQLite's online backup API or `sqlite3 .backup` while the application is healthy. Do not copy only the `.db` file while writers are active because committed data may still be in `-wal`. For an offline copy, stop writers and preserve the database together with any required WAL/SHM sidecars before opening it.

Test restore procedures, Flyway validation, disk capacity, WAL checkpoint behavior, and retention in the target environment. SQLite is suitable for the reference deployment and bounded local workloads; sustained multi-instance or high-write deployments should move the persistence port to a server database.

Runtime `.db`, `-wal`, `-shm`, import, and export files are Git-ignored because sanitized operational data may still be confidential.

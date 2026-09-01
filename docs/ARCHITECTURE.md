# Architecture

Status: approved build direction based on UX baseline `20260901-28`.

## 1. Architectural goals

The reference application must make payment behavior understandable without coupling business rules to GMO request formats. It must preserve a complete audit trail, tolerate duplicate and out-of-order messages, operate when webhooks are unavailable, and remain safe to publish as a public example.

The design uses a modular monolith initially. This keeps SQLite transactions reliable and local development simple while preserving ports that can later move provider, SFTP, or worker capabilities into separate processes.

## 2. System context

```text
Customer / Operator
        |
      Angular
        |
 Spring Boot REST application
        |
  Application use cases
   /        |          \
GMO APIs  SQLite     SFTP server
   \        |          /
 Webhooks, browser returns, inquiry and reconciliation
```

Angular never receives merchant credentials and never calls privileged GMO APIs directly. Browser-side card collection must use the approved GMO integration so PAN/CVC data bypasses this application.

## 3. Modules and dependency direction

| Module | Responsibility | May depend on |
| --- | --- | --- |
| `domain` | Payment instruments, transactions, events, batches, configuration rules, state transitions | Java only |
| `application` | Use cases, idempotency, orchestration, transaction boundaries, ports | `domain` |
| `adapter-gmo` | GMO request/response mapping, signing/authentication, error classification | `application`, `domain` |
| `adapter-persistence` | SQLite schema, repositories, migrations, lock handling | `application`, `domain` |
| `adapter-sftp` | File discovery, download, checksum, staging, parsing, archive policy | `application`, `domain` |
| `adapter-web` | REST controllers, webhook receiver, security, validation, problem details | `application`, `domain` |
| `bootstrap` | Spring configuration, scheduling, feature flags, dependency assembly | all adapters |
| Angular `checkout` | Customer payment experience | generated API client and UI library |
| Angular `configuration` | Draft/publish configuration | generated API client and UI library |
| Angular `operations` | Transaction timelines, API/webhook/SFTP inspector | generated API client and UI library |
| Angular `mit` | Stored instruments and monthly batches | generated API client and UI library |

Provider DTOs must stop at `adapter-gmo`. Controllers must not contain GMO command selection, retry policy, or persistence logic.

## 4. Durable transaction model

`payment_transaction` is the root financial thread. Its current canonical state is a projection; immutable `payment_event` rows are the evidence. Refund, reversal, chargeback, capture, inquiry, retry, webhook, and reconciliation events append to the root thread. Linked resources retain their own local and provider identifiers.

Minimum persistence concepts:

- `application_record`: insurance application and payment-plan context
- `customer`: synthetic/local customer identity used by the reference implementation
- `payment_instrument`: masked provider reference, product code, lifecycle state, Primary/Backup role
- `payment_transaction`: root transaction, amount/currency, CIT/MIT, provider operation, canonical state
- `payment_resource`: child authorization, capture, refund, reversal, dispute, or transfer resource
- `payment_event`: append-only business and transport evidence
- `provider_exchange`: sanitized outbound request and inbound response pair
- `inbound_message`: webhook/protocol payload hash, deduplication key, acknowledgement and linkage status
- `idempotency_record`: command key, request fingerprint, status, linked result
- `configuration_release`: immutable published configuration plus draft metadata
- `debit_batch` and `debit_batch_item`: local Koza monthly grouping and one transaction per customer debit
- `reconciliation_file`, `reconciliation_row`, and `reconciliation_match`: SFTP checkpoints and evidence
- `job_attempt`: retryable background work, next-attempt time, lease, and terminal reason

All persisted payloads are sanitized before storage. Raw PAN, CVC, bank credentials, provider passwords, SFTP private keys, and session tokens are prohibited.

## 5. SQLite concurrency and lock handling

SQLite is appropriate for this reference application when all writes go through short application transactions and background workers use bounded concurrency.

Required safeguards:

- Enable WAL mode, foreign keys, and a configured busy timeout on every connection.
- Keep network calls outside database write transactions.
- Use optimistic version columns on mutable projections.
- Claim jobs with a short atomic update and an expiring lease; never hold a database lock while calling GMO or SFTP.
- Retry `SQLITE_BUSY`/lock failures with bounded exponential backoff plus jitter.
- Use a single-writer scheduler for high-contention maintenance work while allowing concurrent readers.
- Make every command idempotent so a transaction rollback or process restart can be retried safely.
- Expose lock retries, exhausted attempts, WAL growth, and long transactions as operational metrics.
- Provide documented backup/checkpoint procedures and migration tests.

The application must return a retryable service error rather than silently dropping an operation after lock exhaustion.

## 6. Provider failure and retry policy

The GMO adapter classifies results as successful, definitively failed, or unknown. Only failures documented as safe to retry are retried automatically. Timeouts and connection loss after submission are treated as unknown outcomes: perform an inquiry using the same local command/idempotency record before attempting another financial request.

Retry policy is configuration-driven per provider operation and includes maximum attempts, base delay, ceiling, jitter, retryable error codes, and an operator-visible terminal state. The exact GMO-recommended error-code rules must be captured from the applicable official API documentation and tested as contract fixtures before live mode is enabled.

## 7. Webhooks, browser returns, inquiries, and SFTP

Inbound webhooks are optional but, when enabled, are authenticated/validated, durably deduplicated, linked to the transaction, and acknowledged promptly. Business application can occur immediately after durable receipt or asynchronously depending on the GMO protocol contract.

Browser returns are navigation signals, not conclusive financial evidence. If the result is missing or ambiguous, the UI displays a pending state while the backend performs inquiry.

SFTP reconciliation remains available whether webhooks are on or off:

1. Poll or accept a configured file schedule.
2. Download into a private staging area.
3. Require the expected completion marker where applicable.
4. Verify checksum and file identity.
5. Record a durable import checkpoint before parsing.
6. Parse rows idempotently and preserve rejected rows with sanitized reasons.
7. Match each row to the original transaction thread.
8. Record agreement or discrepancy without deleting conflicting evidence.
9. Move/archive only after the configured retention rule succeeds.

## 8. Payment-product boundaries

### Card and PayPay

Customer-initiated payments authorize first. Capture occurs before policy issuance. MIT supports immediate payment and authorization/capture-later where the provider product permits it.

### Real-time bank debit

`bank_direct_realtime` represents `口座直結決済`: registration followed by an immediate debit. Its successful financial result may be represented as paid.

### Koza Furikae Select and first-premium Furikomi

`koza_furikae_select` represents `口座振替（セレクト）`: future debits are submitted in a scheduled monthly batch and receive later asynchronous results.

The customer enrollment use case is one UI journey but two ordered backend operations:

1. Start and confirm Koza bank-account registration.
2. After confirmation only, create the first-premium Furikomi virtual account.

The workflow stores separate registration and first-payment references linked to one application. The confirmation state is `mandate registered + transfer due`, not paid. Later Furikomi notification/SFTP evidence marks the first premium paid. Later monthly debits use the registered mandate and never reuse the Furikomi state machine.

## 9. Configuration and secrets

Payment-method enablement, ordering, thresholds, distribution channels, eKYC rules, language, webhook enablement, SFTP enablement, retry policies, and Koza calendars are versioned configuration. Administrators edit a draft and explicitly publish an immutable release.

Environment-specific public URLs, the local KanjiAI/Cloudflare route, credentials, and key paths are deployment configuration. They are not runtime business configuration and must not be exported with a configuration release.

## 10. Security and observability

- Separate customer, payment-operator, configuration-administrator, and read-only-auditor permissions.
- Require server-side authorization for every action; hidden Angular controls are not security.
- Use structured logs with correlation, application, transaction, and event identifiers.
- Mask secrets and sensitive account data before logs, persistence, or UI rendering.
- Record actor, timestamp, reason, previous state, and result for operator actions.
- Publish health indicators for SQLite, GMO connectivity, webhook backlog, SFTP imports, scheduled jobs, and unresolved reconciliation discrepancies.
- Keep clocks in UTC internally and render explicit IANA time zones in operator views.

## 11. Architecture tests

The build must include dependency-boundary tests, Flyway migration tests, repository concurrency tests, idempotency tests, provider contract fixtures, webhook deduplication tests, SFTP replay tests, state-machine tests, and end-to-end scenarios corresponding to the locked mock acceptance checklist.

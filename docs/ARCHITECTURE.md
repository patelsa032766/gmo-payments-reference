# Architecture

Status: implemented reference architecture based on locked UX baseline `20260901-28`.

## 0. Decision summary

- **Style:** ports-and-adapters modular monolith. This gives one deployable unit and reliable local transactions while keeping GMO, SQLite, SFTP, and HTTP replaceable.
- **System of record:** SQLite owns local intent, canonical projections, and sanitized evidence. GMO remains authoritative for provider execution state.
- **Consistency:** local command reservation is atomic; provider execution and local result persistence are deliberately not one distributed transaction. Idempotency, inquiry, and reconciliation close that gap.
- **Inbound model:** webhooks are optional acceleration, browser returns are navigation signals, and SFTP is an independent reconciliation source.
- **Safety default:** deterministic simulation; live traffic requires an explicit gate plus credentials.
- **UI boundary:** four lazy Angular workspaces share navigation and styling but not workflow state.

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

The GMO adapter classifies results as successful, definitively failed, or unknown. Financial OpenAPI writes receive stable per-local-operation provider idempotency keys. They are never automatically repeated after transport failure or provider 5xx; those outcomes remain operator-visible until inquiry establishes the result. HTTP 4xx is treated as definitive input/provider rejection.

Automatic retry is restricted to authenticated, read-only inquiries. It uses bounded exponential backoff with full jitter and retries 429/502/503/504 or transport failures up to the configured limit. Every terminal failed call is sanitized and persisted. Exact product-specific business error handling must still be validated against the merchant's current GMO contract before production enablement.

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

Card and PayPay customer-initiated execution is an immutable published configuration choice: `AUTH` followed by operator capture, or `CAPTURE` for immediate sale. The browser cannot override it. MIT exposes that choice to the operator for saved Card and PayPay instruments. Real-time bank debit and asynchronous methods retain their product-specific execution semantics.

Capture is a contextual lifecycle command on an `AUTHORIZED` transaction. It is therefore presented in the selected API/Webhooks transaction thread—not Checkout and not the saved-instrument selector. The application reserves the command in SQLite, commits, calls GMO `/order/capture`, and then appends the result and paired provider exchange to the original root transaction. A conclusive rejection leaves the authorization open for review; an ambiguous transport/provider failure becomes `UNKNOWN` and must be inquired before any repeat.

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

The predefined-customer selector, due-today amount, and checkout language form a
separate local demonstration scenario stored in `checkout_experience_settings`
and the selected `application_record`. Its optional authentication flag applies
only to configuration mutations. Financial operator commands remain authorized
independently and cannot be bypassed by changing this scenario.

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

## 12. Runtime and deployment view

The reference topology is one Angular static deployment and one Spring Boot process with one local SQLite volume:

```text
Public HTTPS edge
  /                 -> Angular static assets
  /api, /actuator   -> Spring Boot (operator/API access policy at edge)
  /webhooks/gmo/*   -> Spring Boot (edge injects ingress credential)
                              |
                    private SQLite volume
                       /             \
                 GMO egress       SFTP egress
```

The process should run as a single writer instance for SQLite. Horizontal application replicas require a different persistence adapter or strict ownership that guarantees only one writer. The public edge must prevent bypassing the webhook-header injection path, terminate TLS, apply request-size/rate limits, and separate operator authentication from customer checkout access.

Environment/secret-manager values configure provider credentials, callback origin, ingress token, SFTP identity, and the operator token. SQLite stores only the local-demo boolean that decides whether the operator token is enforced; it never stores the credential. The switch applies uniformly to configuration, capture, MIT, payment-order, batch, and manual reconciliation mutations. SQLite configuration releases otherwise contain business rules only and can therefore move between environments without exporting secrets.

## 13. Key workflows

### Card recurring checkout

```text
Angular -> GMO MP Token JS: tokenize PAN/CVC
Angular -> Spring: MP token + holder + Idempotency-Key
Spring -> SQLite: reserve transaction/idempotency event
Spring -> GMO /credit/charge: AUTH
Spring -> GMO /credit/storeCard: successful charge accessId
Spring -> SQLite: authorization, both exchanges, reusable instrument
Spring -> Angular: confirmation
```

Failure to store the card after a successful authorization marks the transaction for attention but never mislabels or automatically repeats the financial authorization.

### PayPay recurring checkout

```text
Spring -> GMO /wallet/authorizeAccount -> customer redirect
GMO -> browser return envelope
Spring -> GMO /order/inquiry -> require REGISTER
Spring -> GMO /wallet/on-file/charge -> first AUTH
Spring -> SQLite -> reusable PayPay instrument + complete thread
```

### Koza enrollment and later collection

```text
Checkout: register Koza mandate -> authoritative result inquiry
          -> create first-premium Furikomi instructions
Monthly:  operator batch -> one GMO debit request per selected mandate
          -> async webhook and/or SFTP results update each original thread
```

Koza and real-time bank debit share neither product code nor financial state machine.

## 14. Known production hardening decisions

The reference uses a shared operator token to make local workflows runnable and exposes a global, default-on enforcement switch for local testing. Disabling that switch permits every operator mutation without the token and must never be treated as a production security model. A production deployment must keep protection enabled and replace it with organization identity, authorization, CSRF/session policy, audit attribution, and role enforcement. Product contracts, callback allowlists, retention periods, observability export, backup objectives, disaster recovery, and a server database decision remain deployment-owner responsibilities.

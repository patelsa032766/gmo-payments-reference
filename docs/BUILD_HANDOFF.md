# Build Handoff

Status: functional reference build implemented from approved mock baseline `20260901-28`.

## Delivered

- Java 21 / Spring Boot 4.1.1 multi-module Maven build with checked-in wrapper.
- Angular 22 application reproducing the separate Checkout, Configuration, API & Webhooks, and MIT workspaces.
- Independently runnable static mock retained in `ui-mock/`.
- Flyway SQLite schema for configuration, applications, instruments, transactions, events, provider exchanges, inbound messages, idempotency, Koza batches, SFTP reconciliation, and jobs.
- Draft/publish configuration with enablement, recurring/monthly rules, thresholds, order, Card/PayPay CIT execution policy, channels, eKYC rule, and Japanese/English copy.
- Method-specific checkout accordion for enabled and disabled method configurations.
- GMO OpenAPI and idPass adapters for Card, PayPay, real-time bank debit, Koza Furikae Select, Kombini, Pay-easy, and Furikomi.
- Card authorization plus store-card; PayPay account authorization plus first authorization; real-time bank registration plus immediate debit.
- Combined Koza registration plus first-premium Furikomi instruction journey.
- Saved-method Primary/Backup preferences; Card/PayPay MIT immediate-sale or auth/capture choice; individual bank debit; and monthly Koza batch submission.
- Contextual Card/PayPay capture on authorized transaction threads using GMO `/order/capture`, operator authentication, idempotency, and unknown-outcome protection.
- Chronological transaction thread with paired outbound request/inbound response evidence.
- Optional edge-authenticated webhook ingestion and independent pinned-host SFTP import.
- Safe-read inquiry retry with bounded jitter; no automatic retry of ambiguous financial writes.
- Deterministic simulation default, operator action authentication, payload sanitization, RFC Problem Details, health checks, and rich developer documentation.

## Baseline invariants

- Do not replace `ui-mock`; create a new mock baseline before intentionally changing approved UX.
- Keep real-time bank debit (`bank_direct_realtime`) and Koza Furikae Select (`koza_furikae_select`) separate in domain codes, provider mappings, storage, UI labels, MIT paths, and reporting.
- Never send raw card data to Spring or persist it anywhere in this application.
- Never infer financial success from a browser return alone.
- Keep every lifecycle event on the original root transaction thread.
- Preserve exactly one Primary and at most one distinct Backup active instrument per customer.
- Never hold a SQLite transaction open across provider or SFTP I/O.
- Never automatically repeat an uncertain financial write.

## Verification gate

Before merging or publishing:

```bash
./scripts/check.sh
git status --short
```

Additionally verify in simulation mode:

1. All four checkout accordions expand.
2. A synthetic card checkout reaches Confirmation.
3. The new transaction appears first in `/operations` with customer and provider events.
4. Configuration creates a draft, can discard it, and publishes under the same global operator-token policy used by capture, MIT, payment-order, batch, and reconciliation actions.
5. MIT shows Primary and Backup and rejects the same instrument in both roles.
6. Koza items can be selected and submitted only through the monthly batch workflow.
7. Disabled webhooks return 404 and disabled SFTP makes no connection.
8. No `.env.local`, database/WAL, private key, tunnel config, or downloaded reconciliation file is tracked.

Live GMO validation is a separate explicit gate because it can create sandbox financial records. Test one product at a time with synthetic identities and inspect the persisted transaction thread before enabling the next product.

## Production-owner decisions

The code is intentionally runnable without prescribing an organization's platform. Before production use, decide and document:

- customer/operator identity and role model replacing the local shared operator token;
- ingress topology, direct-origin restriction, rate limiting, and webhook source controls;
- exact GMO product versions, enabled banks/stores, error-code matrix, cutoff calendar, and sandbox-to-production promotion;
- retention/redaction for provider exchanges, webhooks, SFTP rows, and audit events;
- RPO/RTO, SQLite online backup/checkpoint monitoring, or migration to a server database;
- metrics/log export, alert thresholds, unresolved-unknown and reconciliation queues;
- SFTP file specification, timezone/cutoff semantics, archive ownership, and key rotation;
- refund/capture/void/chargeback operator permissions and any four-eyes approval requirement;
- dependency/secret scanning and public-repository release automation.

## Recommended next engineering slices

1. Add contract fixtures captured from the account's current GMO sandbox documentation for every enabled product.
2. Add repository lock-contention and process-restart integration tests around unknown outcomes.
3. Replace the local operator token with real identity and authorization.
4. Add explicit void/refund operator commands and any four-eyes approval required by the policy-issuance workflow.
5. Add scheduled inquiry jobs for unknown results and operational alerting.
6. Validate the merchant's actual SFTP layout and add exact parser fixtures before turning on scheduled polling.
7. Add deployment manifests and end-to-end tests for the chosen public HTTPS edge.

These are production-hardening/integration decisions, not missing mock screens. The current app is a complete functional reference in simulation mode and contains the live adapter boundaries needed for controlled sandbox validation.

# Build Handoff

Status: ready to begin implementation from approved mock baseline `20260901-28`.

## Non-negotiable baseline

- Preserve `ui-mock` as the runnable design reference.
- Build Checkout, Configuration, API & Webhooks, and MIT as separate Angular routes/workspaces.
- Reproduce behavior, hierarchy, responsive layout, English/Japanese copy, and low-weight typography before proposing visual changes.
- Keep all prototype data synthetic. Do not copy credentials, tunnels, database files, or provider responses from another local project.
- Keep real-time bank debit and Koza Furikae Select separate in code, storage, configuration, and operator language.

## Implementation sequence

1. Establish the multi-module Java build, Angular workspace, shared formatting, static analysis, test conventions, and continuous-integration commands.
2. Implement provider-independent domain types, explicit state machines, use-case ports, error taxonomy, idempotency, and unit tests.
3. Add Flyway-managed SQLite schema, WAL/busy-timeout configuration, repositories, lock retry behavior, and migration/concurrency tests.
4. Implement configuration drafts/releases and payment-method eligibility APIs; build the Configuration route and reproduce the approved Checkout filtering/order behavior.
5. Implement Checkout orchestration with simulated provider adapters first, including all success, failure, cancellation, and unknown/inquiry states.
6. Implement the combined Koza registration + first-premium Furikomi workflow as one customer journey with two linked backend operations.
7. Add durable transaction/event/provider-exchange storage and build the API & Webhooks timeline/inspector.
8. Implement authenticated webhook receivers behind feature flags, followed by SFTP acquisition, parsing, deduplication, and reconciliation.
9. Implement stored-instrument roles, individual MIT execution, removal, lifecycle actions, and monthly Koza batch fan-out/async results.
10. Add production-safe GMO adapters operation by operation using official contract fixtures; keep live mode disabled until configuration, secrets, and sandbox validation are complete.
11. Complete documentation, architecture decision records, local setup, troubleshooting, recovery, security review, and public-repository secret/dependency scans.

## Definition of done for each slice

Every vertical slice must include:

- Domain behavior and state-transition tests
- Application use case with an idempotency strategy
- SQLite migration and repository tests
- REST contract and error responses
- Angular route/components with responsive and accessibility checks
- Sanitized operational events visible in the transaction thread
- Feature-flag/configuration documentation
- Failure, timeout/unknown, retry/inquiry, and restart behavior
- Developer-facing comments where the reasoning or provider constraint is not obvious

Comments should explain invariants, risks, and protocol decisions. They should not narrate self-evident syntax.

## First build milestone

The first runnable milestone should use simulated provider adapters and deliver:

- Spring Boot health endpoint and SQLite migration on startup
- Versioned configuration read/draft/publish API
- Angular shell with the four approved routes
- Checkout summary and eligibility-driven method list matching the mock
- Persistent application/payment attempt/event records
- Deterministic test outcomes for success, failure, cancellation, and unknown result
- Local developer commands and automated tests documented in the root README

No real GMO, webhook, SFTP, or financial call is required for this first milestone.

## Decisions still requiring owner input before public release

- Authentication approach for operator/admin routes
- Supported operating systems and deployment target
- Retention period for event payloads, webhook messages, and reconciliation files
- Exact GMO sandbox credentials and SFTP schedule supplied only through private deployment configuration

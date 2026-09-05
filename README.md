# GMO Payments Reference Application

A modular Java 21, Spring Boot, Angular, and SQLite reference implementation for customer checkout and payment operations. It includes a safe deterministic mode for public use and explicitly gated GMO sandbox adapters for Card, PayPay, real-time bank debit, cash methods, and Koza Furikae Select.

The approved customer/operator experience is preserved separately in `ui-mock/` at baseline `20260901-28`. The Angular application implements that baseline as four routes:

| Route | Audience | Purpose |
| --- | --- | --- |
| `/checkout` | Customer | Eligibility-filtered checkout, method-specific collection, provider handoff, and confirmation. |
| `/configuration` | Administrator | Select a predefined test customer, amount, and language; publish method visibility, order, thresholds, plan eligibility, and CIT policy. |
| `/operations` | Operator/auditor | Transaction list, chronological lifecycle thread, paired outbound/inbound evidence, webhook setup, and SFTP status. |
| `/mit` | Payment operator | Saved instruments, Primary/Backup assignment, individual MIT charges, and monthly Koza batches. |

## What is implemented

- Server-side method enablement, ordering, plan rules, eKYC rules, channels, amount thresholds, and localized labels.
- Versioned configuration drafts with explicit publish/discard commands.
- A SQLite-backed test checkout scenario: predefined customer/application, due-today amount, and English/Japanese language survive navigation and restart.
- Card browser tokenization, versioned CIT auth/immediate-sale policy, reusable-card registration, and contextual capture.
- PayPay recurring-account authorization followed by a configured first authorization or immediate sale, with contextual capture where required.
- Real-time bank debit (`口座直結決済`) registration followed immediately by a debit.
- Koza Furikae Select (`口座振替（セレクト）`) registration followed by Furikomi instructions for the first premium; later monthly requests use a separate batch workflow.
- One-time Kombini, Pay-easy, and Furikomi command mappings.
- Saved Card and PayPay MIT commands with operator-selected auth/capture-later or immediate sale, plus real-time bank debit.
- Exactly one Primary and optionally one Backup active instrument per customer; the newest successful registration becomes Primary.
- Koza batch reservation/submission with one durable transaction per debit request and asynchronous result ingestion.
- Append-only transaction threads containing checkout, API, browser return, webhook, retry/inquiry, reconciliation, refund, and chargeback evidence when received.
- Optional authenticated webhooks and independent SFTP reconciliation.
- SQLite WAL mode, foreign keys, busy timeout, bounded lock retries, idempotency records, and Flyway migrations.
- Deterministic simulation by default. No clean clone makes a GMO request.

## Prerequisites

- JDK 21
- Node.js 24 LTS and npm
- Bash-compatible shell for the helper scripts

The Maven wrapper is committed, so a separate Maven installation is unnecessary.

## Run locally in simulation mode

Terminal 1:

```bash
cd /path/to/gmo-payments-reference
export OPERATOR_API_TOKEN=local-operator-token
./scripts/run-backend.sh
```

The helper installs all reactor modules first, then starts Spring Boot. That first install matters on a clean clone because `spring-boot:run` executes from `bootstrap` and needs the sibling adapter artifacts.

Terminal 2:

```bash
cd /path/to/gmo-payments-reference/frontend
npm ci
npm start
```

Open `http://127.0.0.1:4200`. Angular proxies `/api` and `/actuator` to `http://127.0.0.1:8080`.

```bash
curl http://127.0.0.1:8080/actuator/health
```

If Homebrew's JDK is not selected on macOS:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH=/usr/local/opt/openjdk@21/bin:$PATH
```

## Environment configuration

Copy `.env.example` to `.env.local`, which is Git-ignored, then edit only local values. `run-backend.sh` loads this file automatically and parses dotenv assignments without executing them as shell code, so values containing spaces are supported:

```bash
cp .env.example .env.local
# Edit .env.local with your private values.
./scripts/run-backend.sh
```

To reuse another implementation's private sandbox environment without copying
it or hard-coding its location in this public repository, select it only for
the local process:

```bash
GMO_ENV_FILE=/private/path/to/existing/.env \
GMO_LIVE_CALLS_ENABLED=true \
OPERATOR_API_TOKEN=choose-a-local-operator-token \
./scripts/run-backend.sh
```

If the selected legacy environment contains a non-SQLite `DATABASE_URL`, the
launcher intentionally ignores that value and uses this application's SQLite
database. The Configuration page's **Save changes** action writes a draft and
publishes it as one active version; the choice then survives navigation and
application restarts.

For low-friction local UI testing, **Require token for operator actions** can be
disabled in the saved test scenario. It is one global switch covering
configuration draft/publish/discard, capture, MIT charges, instrument-role
changes, Koza batch submission, and manual reconciliation imports. The
environment credential is never written to SQLite; SQLite stores only whether
the local demo should enforce it.

Turning the switch off enables these operator actions immediately without a
credential. This behavior exists strictly for local demonstration convenience.
Production deployments must keep it enabled, must not expose the Configuration
route as an unauthenticated control plane, and should replace the shared token
with real identity and role-based authorization.

Configuration precedence is:

1. Environment/secret-manager values
2. Ignored local overrides
3. Published SQLite business configuration
4. Safe generic defaults

Provider secrets, public tunnel addresses, SFTP keys, and ingress tokens are deployment settings, not publishable checkout configuration. The operator UI deliberately shows only safe status and endpoint information.

## Connect a GMO sandbox

Keep `GMO_LIVE_CALLS_ENABLED=false` while configuring. Supply your own sandbox values through `.env.local` or a secret manager:

```bash
GMO_SHOP_ID=...
GMO_SHOP_PASS=...
GMO_SITE_ID=...
GMO_SITE_PASS=...
GMO_LIVE_CALLS_ENABLED=true
```

The environment names are intentionally compatible with the companion Flask implementation, so developers can reuse private environment values without copying a credential file into this repository. Confirm the base URLs and enabled products against your own GMO contract before enabling live calls.

Card PAN/CVC never enters Spring Boot. Angular loads GMO's MP Token browser library from `GMO_MP_TOKEN_JS_URL`, receives a one-use token, and sends only that token and the cardholder name to the backend.

The application number is a stable insurance-business reference and is sent in
GMO client field 1. Every checkout or MIT attempt receives a separate local
transaction ID, which is used as GMO's `orderId`. This prevents a customer retry
from colliding with an earlier authorization while preserving end-to-end
application correlation.

Switching from simulation to live mode does not rewrite historical transaction
evidence. Prototype rows remain labelled as simulated in the transaction thread
and deliberately do not offer a Capture action; only a new, successful GMO
sandbox authorization produces a capturable provider order.

## Browser returns and webhook ingress

A local application needs a public HTTPS origin for GMO browser returns and notifications. Use Cloudflare Tunnel, ngrok, an ingress controller, or another reverse proxy and configure its public origin only in local/deployment environment variables.

```bash
DEV_PUBLIC_BASE_URL=https://your-public-host.example
GMO_BROWSER_RETURN_BASE_URL=https://your-public-host.example
GMO_CUSTOMER_APP_BASE_URL=http://127.0.0.1:4200
GMO_WEBHOOKS_ENABLED=true
GMO_WEBHOOK_INGRESS_TOKEN=a-long-random-secret
GMO_WEBHOOK_CSRF_SECRET=an-independent-random-hmac-secret
```

Public callbacks are:

- `POST /webhooks/gmo/openapi`
- `POST /webhooks/gmo/protocol`
- `POST /webhooks/gmo/protocol/return/bank-direct`
- `POST /webhooks/gmo/protocol/return/koza-furikae`
- `GET /api/v1/gmo/returns/paypay-registration`

OpenAPI requests include a URL-safe, per-order `merchant.csrfToken` derived with
`GMO_WEBHOOK_CSRF_SECRET`; GMO echoes it in the webhook and the application
validates it in constant time. The OpenAPI endpoint also accepts
`X-Webhook-Ingress-Token` when a trusted edge injects it. Legacy protocol
notifications do not carry the OpenAPI token and therefore require that edge
header. Never put either secret in a URL. Browser-return endpoints use provider
references and server-side inquiry/integrity validation instead of treating the
browser as financial authority.

For local tunnels, publish only the callback paths instead of the whole
application. A Cloudflare ingress can use the following shape; replace the
hostname, tunnel ID, credentials path, and origin for your environment:

```yaml
ingress:
  - hostname: payments.example.com
    path: ^/api/v1/gmo/returns/.*
    service: http://127.0.0.1:8080
  - hostname: payments.example.com
    path: ^/webhooks/gmo/.*
    service: http://127.0.0.1:8080
  - service: http_status:404
```

When `GMO_WEBHOOKS_ENABLED=false`, notification endpoints respond as unavailable and GMO webhook URLs are omitted where the product permits. Browser-return completion and SFTP reconciliation remain independent.

## SFTP reconciliation

SFTP can operate whether webhooks are enabled or disabled. Enable it only with a pinned `known_hosts` file and one authentication mode:

```bash
SFTP_RECONCILIATION_ENABLED=true
SFTP_HOST=sftp.example.com
SFTP_USERNAME=merchant-account
SFTP_PRIVATE_KEY_PATH=/absolute/private/path/id_ed25519
SFTP_KNOWN_HOSTS_PATH=/absolute/private/path/known_hosts
SFTP_INCOMING_PATH=/incoming
SFTP_ARCHIVE_PATH=/archive
```

The poller accepts only matching filenames with a ready marker, enforces a byte limit, checksum-deduplicates files, stages rows durably, links rows to the original transaction thread, and archives only after import. A protected manual poll is available at `POST /api/v1/reconciliation/sftp/import` with `X-Operator-Token`.

## Payment and retry safety

Financial writes receive stable provider idempotency keys where GMO supports them. A timeout or 5xx after a financial submission is classified as an unknown outcome and is not automatically repeated. The operator sees the ambiguous exchange and the application must establish the result through a safe inquiry before another financial command is allowed.

Only read-only inquiries retry automatically. The retry is bounded exponential backoff with full jitter, configured through `GMO_SAFE_READ_*`. HTTP 4xx responses are definitive; 429/502/503/504 inquiry failures are eligible for retry. Sanitized failed calls remain visible in the same transaction thread.

## SQLite runtime and locking

The default database is `backend/bootstrap/runtime/gmo-payments.db`. Flyway owns the schema. SQLite uses WAL, `foreign_keys=on`, a 5-second busy timeout, a small Hikari pool, optimistic versions, short write transactions, and bounded jittered retries for `SQLITE_BUSY`.

No database transaction is held across GMO or SFTP network I/O. Intent is committed, the external operation runs, and sanitized evidence is appended in a new transaction. See `docs/DATABASE.md` for tables, state ownership, backup, and recovery.

## Verification

Run the complete deterministic suite:

```bash
./scripts/check.sh
```

Or separately:

```bash
cd backend && ./mvnw test
cd ../frontend && npm test -- --watch=false && npm run build
```

Live sandbox calls are intentionally excluded from automated tests. Validate them product by product with synthetic data and inspect `/operations` after each call.

## Repository layout

```text
backend/domain                 provider-independent types
backend/application            use cases and outbound ports
backend/adapter-gmo            OpenAPI/idPass mapping and clients
backend/adapter-persistence    SQLite repositories and Flyway migrations
backend/adapter-sftp           pinned-host SFTP acquisition
backend/adapter-web            REST, returns, webhooks, and Problem Details
backend/bootstrap              Spring Boot assembly and configuration
frontend                       Angular customer/operator application
ui-mock                        independently runnable locked mock
docs                           architecture, API, database, and handoff guides
```

## Approved mock

The mock remains independently runnable and never requires Java, Angular, or GMO credentials:

```bash
python3 -m http.server 8081 --directory ui-mock
```

Open `http://127.0.0.1:8081/?v=20260901-28`.

## Public-repository safety

Before publishing, verify that no Shop/Site credentials, tokens, tunnel credentials, SFTP keys, runtime databases, downloaded files, or real customer/provider payloads are tracked. All committed examples are synthetic. `.env.local`, key files, SQLite/WAL files, reconciliation data, and tunnel configuration are ignored.

## Further documentation

- `docs/ARCHITECTURE.md` — boundaries, workflows, state/evidence model, security, and failure policy
- `docs/API.md` — REST commands, headers, callbacks, and examples
- `docs/DATABASE.md` — schema and SQLite interaction/locking rules
- `docs/BUILD_HANDOFF.md` — implemented scope, validation gates, and remaining production decisions
- `backend/README.md` — backend module and development guide

## License

Released under [The Unlicense](UNLICENSE), a public-domain dedication that permits unrestricted use, modification, distribution, commercial use, and private use. The software is provided without warranty.

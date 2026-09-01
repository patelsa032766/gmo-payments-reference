# GMO Payments Reference Application

This repository is the build workspace for a modular insurance-payment reference application using Java, Spring Boot, Angular, and SQLite. The customer and operator experience was approved on 1 September 2026 and is preserved as static mock baseline version 28.

## Project status

- UX baseline: approved and locked
- Baseline version: `20260901-28`
- Build milestone: first runnable vertical slice implemented
- Backend: Java 21 / Spring Boot 4.1.1 modular Maven reactor
- Frontend: Angular 22.1 standalone application with four lazy-loaded routes
- Persistence: Flyway-managed SQLite configuration baseline with WAL and busy timeout
- Live GMO calls: not enabled
- Public GitHub repository: `patelsa032766/gmo-payments-reference`

The approved mock is independently runnable from [`ui-mock`](./ui-mock/README.md). Application code must be added beside that directory rather than replacing it. Any future UX proposal must use a new mock version and must not silently rewrite the approved baseline.

## Run the application locally

Prerequisites are JDK 21 and Node.js 24. The Maven version is pinned by the checked-in wrapper; npm installs the Angular toolchain from `package-lock.json`.

Start the backend in terminal one:

```bash
cd backend
./mvnw test
./mvnw -pl bootstrap -am install -DskipTests
./mvnw -pl bootstrap spring-boot:run
```

If macOS cannot locate a Homebrew JDK automatically, set it for that terminal first:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH=/usr/local/opt/openjdk@21/bin:$PATH
```

Start Angular in terminal two:

```bash
cd frontend
npm install
npm start
```

Open `http://127.0.0.1:4200`. Angular proxies `/api` and `/actuator` to Spring Boot on port `8080`. Useful direct checks are:

```bash
curl http://127.0.0.1:8080/actuator/health
curl 'http://127.0.0.1:8080/api/v1/checkout/options?channel=PA&amountJpy=10000&monthly=true&ekycVerified=true&language=en'
```

To run the frontend checks:

```bash
cd frontend
npm test -- --watch=false
npm run build
```

### What works in this slice

- Spring Boot starts, Flyway creates the SQLite schema, and Actuator reports health.
- The published configuration release contains enabled state, recurring eligibility, channel coverage, amount thresholds, localized labels, and display order.
- `GET /api/v1/checkout/options` applies those rules server-side; Angular renders only the returned methods.
- `GET /api/v1/configuration/active` feeds the Configuration route.
- Checkout supports method selection and a non-financial confirmation step.
- API & Webhooks and MIT reproduce the approved operator layout but are explicitly non-executing until their command/event slices are implemented.

No button in this milestone sends a GMO request or financial transaction.

## Approved workspaces

| Workspace | Audience | Purpose |
| --- | --- | --- |
| Checkout | Customer | Select an eligible payment method, complete the provider interaction, and receive a confirmation or payment instructions. |
| Configuration | Configuration administrator | Manage method ordering, enablement, thresholds, language, integration addresses, feature flags, and draft/publish state. |
| API & Webhooks | Payment operator and auditor | Inspect durable transaction threads, paired requests/responses, webhooks, inquiries, retries, and SFTP reconciliation. |
| MIT Transactions | Payment operator | Charge saved instruments, assign Primary/Backup roles, remove stored methods, and submit monthly Koza Furikae batches. |

## Locked payment decisions

- Monthly Card and PayPay use authorization followed by capture before policy issuance.
- Real-time bank debit (`口座直結決済`) registers an account and immediately requests a debit.
- Koza Furikae Select (`口座振替（セレクト）`) is a different product and state machine.
- The combined Koza customer journey first registers the future monthly mandate and, only after confirmation, automatically creates Furikomi instructions for the first premium.
- A successful Koza registration does not mean the first premium is paid. The first premium remains due until the transfer is reconciled.
- One-time policies may expose all enabled methods that satisfy channel, eKYC, and amount rules.
- A customer has exactly one Primary stored method and optionally one distinct Backup. The latest successfully registered method becomes Primary by default; execution fallback behavior is deferred.
- Refunds, chargebacks, disputes, notifications, inquiries, and reconciliation evidence remain linked to the original durable transaction thread.

## Source layout

```text
frontend/                       Angular customer and operator application
backend/
  domain/                       Provider-independent payment domain
  application/                  Use cases, transactions, ports, and policies
  adapter-gmo/                  GMO OpenAPI and protocol adapters
  adapter-persistence/          SQLite repositories and Flyway migrations
  adapter-sftp/                 Reconciliation transport, staging, and parsing
  adapter-web/                  REST, webhooks, security, and error mapping
  bootstrap/                    Spring Boot assembly and runtime configuration
docs/                           Architecture and build decisions
ui-mock/                        Locked static UX baseline
```

The first four backend modules plus `bootstrap` now exist. `adapter-gmo` and `adapter-sftp` remain intentional future modules so no simulated behavior can be mistaken for a live integration. Dependencies point inward: adapters depend on application ports, and the domain has no Spring or provider dependency.

See [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) for system boundaries, [`docs/DATABASE.md`](./docs/DATABASE.md) for SQLite ownership and lock rules, [`docs/API.md`](./docs/API.md) for the current contract, and [`docs/BUILD_HANDOFF.md`](./docs/BUILD_HANDOFF.md) for the implementation sequence and acceptance gates.

## Configuration precedence

Runtime configuration will resolve in this order:

1. Locked environment variables or secret manager values
2. Gitignored local configuration
3. Published SQLite runtime configuration
4. Safe generic defaults

Webhook support is optional. When disabled, callback fields are omitted where supported, while browser-return handling, provider inquiries, and SFTP reconciliation remain independently configurable. Personal Cloudflare/KanjiAI values belong only in local overrides and must never be added to the public repository.

## Public-repository safety

Before publishing:

1. Confirm no GMO Shop ID, Shop Password, Site ID, Site Password, access token, SFTP key, customer data, tunnel credential, or downloaded reconciliation file is tracked.
2. Keep examples unmistakably synthetic and masked.
3. Run the complete backend and frontend test suites.
4. Run a dependency and secret scan.
5. Confirm the public-domain dedication remains appropriate for every contribution.

## License

This project is released under [The Unlicense](./UNLICENSE), a public-domain dedication with no conditions on use, modification, distribution, commercial use, or private use. The software is provided without warranty.

## View the approved mock

```bash
python3 -m http.server 8081 --directory "/Users/Samir/Documents/GMOPaymentsJava/ui-mock"
```

Open `http://127.0.0.1:8081/?v=20260901-28`.

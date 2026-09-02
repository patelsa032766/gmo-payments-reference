# Backend Developer Guide

The backend is a Java 21 modular monolith assembled by Spring Boot 4.1.1. The module split is enforced by Maven dependencies so provider and persistence details cannot leak into the payment domain by convenience.

## Modules

- `domain`: immutable payment/configuration concepts and invariants; Java only
- `application`: use cases and outbound ports; depends only on `domain`
- `adapter-gmo`: OpenAPI/idPass request factories, authenticated clients, sanitization, error classification, and safe-read retry
- `adapter-persistence`: SQLite repositories, Flyway migrations, idempotency, transaction threads, and lock retry support
- `adapter-sftp`: strict host-key SFTP discovery/download/archive behind the reconciliation port
- `adapter-web`: versioned REST controllers, browser returns, optional webhooks, validation, CORS, and Problem Details mapping
- `bootstrap`: executable Spring Boot assembly and environment configuration

Adapters must never be imported by `domain` or `application`. Provider DTO/field names stop in `adapter-gmo`; SQL stops in `adapter-persistence`; JSch types stop in `adapter-sftp`.

## SQLite lifecycle

The documented Maven launch creates `backend/bootstrap/runtime/gmo-payments.db`. Override `DATABASE_URL` with an explicit absolute path in other launch/deployment arrangements. Flyway owns schema evolution; do not edit an applied migration. Add a new `V{number}__description.sql` migration instead.

The JDBC URL enables WAL, a 5-second SQLite busy timeout, and foreign keys. Hikari is capped at four connections. Application-level lock retries are bounded and jittered. Network calls must occur outside database transactions: write an intent, commit, call the provider, and then persist the sanitized result in a new short transaction.

Runtime database files and WAL sidecars are gitignored. The empty directory is retained only so a clean clone can start without manual setup.

## Commands

```bash
./mvnw test
./mvnw -pl bootstrap -am package
./mvnw -pl bootstrap -am install -DskipTests
./mvnw -pl bootstrap spring-boot:run
```

On a clean clone, run the install command before `spring-boot:run`, or use `../scripts/run-backend.sh`. The module-only run goal does not automatically resolve sibling reactor artifacts that have never been installed locally.

## Provider call discipline

Use cases reserve local intent and commit before calling an adapter. GMO clients must return sanitized evidence or attach that evidence to a classified exception so failure paths remain visible to operators. Financial writes use a stable provider idempotency key where available but are never automatically repeated after an ambiguous result. Only explicitly read-only inquiries may use `GmoSafeReadRetryExecutor`.

Browser callbacks call `BrowserReturnService`, which finds the pre-existing transaction by provider reference and invokes a server-to-server inquiry/continuation. A callback must never construct a new payment transaction or declare success from browser fields alone.

## Adding a payment method

1. Add a stable `PaymentMethodCode` public value and configuration migration.
2. Define the state/instrument behavior in `domain` and a use-case port in `application`.
3. Implement provider mapping only in `adapter-gmo`, with request-shape tests and sanitization assertions.
4. Persist intent, event, provider exchange, idempotency, and reusable-instrument behavior in `adapter-persistence`.
5. Expose the method through the existing checkout command contract and add an Angular detail component.
6. Add success, definitive failure, cancellation, unknown/inquiry, duplicate, and restart tests.
7. Document environment settings and live-enable gates before adding credentials anywhere.

The build enforces Java 21 and Maven 3.9 or newer. Secrets must be supplied through environment variables or an ignored `application-local.yml`; never put GMO, SFTP, or tunnel credentials in a committed YAML file.

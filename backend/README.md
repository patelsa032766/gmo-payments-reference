# Backend Developer Guide

The backend is a Java 21 modular monolith assembled by Spring Boot 4.1.1. The module split is enforced by Maven dependencies so provider and persistence details cannot leak into the payment domain by convenience.

## Modules

- `domain`: immutable payment/configuration concepts and invariants; Java only
- `application`: use cases and outbound ports; depends only on `domain`
- `adapter-persistence`: SQLite repository, Flyway migrations, and lock retry support
- `adapter-web`: versioned REST controllers, validation, CORS, and Problem Details mapping
- `bootstrap`: executable Spring Boot assembly and environment configuration

Future `adapter-gmo` and `adapter-sftp` modules will implement application ports. They must never be imported by `domain` or `application`.

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

The build enforces Java 21 and Maven 3.9 or newer. Secrets must be supplied through environment variables or an ignored `application-local.yml`; never put GMO, SFTP, or tunnel credentials in a committed YAML file.

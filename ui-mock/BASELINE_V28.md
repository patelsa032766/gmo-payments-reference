# Approved UI Mock Baseline — Version 28

Approval date: 1 September 2026
Status: locked for implementation

This directory is the independent, dependency-free UX specification for the Java/Angular/Spring Boot/SQLite build. Version 28 was approved after completing the customer Checkout, Configuration, API & Webhooks, MIT transactions, transaction lifecycle, stored-method Primary/Backup roles, monthly Koza Furikae batch, and combined Koza registration + first-premium Furikomi journey.

## Lock rules

1. Application implementation belongs outside `ui-mock`.
2. Do not change version 28 to make the production build easier.
3. If an implementation discovery requires a UX change, copy the proposal into a new mock version, advance all asset query strings, document the delta, and obtain approval before changing the implementation baseline.
4. Keep the mock static and incapable of sending GMO, webhook, SFTP, or financial requests.
5. Keep environment-specific URLs in browser storage only; never hardcode the local KanjiAI/Cloudflare route.

## Approved entry point

```text
http://127.0.0.1:8081/?v=20260901-28
```

## Runtime files covered by the baseline

- `index.html`
- `styles.css`
- `app.js`
- `ops-mock.css`
- `ops-mock.js`

The checksum manifest below records the exact approved runtime artifact. Documentation may receive clarifications without changing the runtime baseline; any runtime-file checksum change requires a new version and review.

## Validation completed at approval

- JavaScript syntax checks passed for both scripts.
- Desktop and 390 px responsive layouts were inspected.
- No horizontal overflow was found at the tested narrow width.
- Checkout success, inline validation, cancellation/failure design, and unknown-result inquiry were exercised.
- Combined Koza registration withheld Furikomi instructions until registration success was confirmed.
- English/Japanese method copy and the Koza feature switch were verified.
- Operator transaction, webhook/SFTP, MIT, stored-method, and monthly Koza behaviors are documented in `SESSION_HANDOFF.md`.

## Checksum manifest

Run the following from this directory and compare it with the manifest committed below:

```bash
shasum -a 256 index.html styles.css app.js ops-mock.css ops-mock.js
```

```text
a42875e905107535b0983ce72c3f8cc629c94b622de67b478e33627c21090806  index.html
6be1571847d4dfb5ab4174ff7c0c6388f2c622af9e89f23978dc27c0c2be200f  styles.css
cfe68684be153077a4c59706ab9dc40731cf775a1cc668a066831f7d43a4288c  app.js
f3675fafbb60a960718482be68cdd985e90626ace9c71f93a54631ca9596d7ba  ops-mock.css
8ca36cd4ad862692c924d39cb60b25d7c1a50b802648b02175820b399dc55b91  ops-mock.js
```

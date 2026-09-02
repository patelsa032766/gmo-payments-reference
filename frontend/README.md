# Angular Developer Guide

This Angular 22.1 application implements the approved customer and operator shell. Each area is a lazy-loaded standalone route:

- `/checkout`: eligibility-driven method accordions, GMO browser handoff/tokenization, errors, and confirmation
- `/configuration`: active/draft configuration, enablement, ordering, thresholds, language preview, and publish/discard
- `/operations`: transaction search plus chronological event and paired request/response inspection
- `/mit`: saved instruments, Primary/Backup roles, individual MIT, and Koza monthly batches

Shared API types and HTTP access live in `src/app/core/api`. Feature components must not construct backend URLs or call GMO directly. The backend owns credentials, eligibility, idempotency, and all financial decisions.

## Commands

Use Node.js 24 and npm 11:

```bash
npm install
npm start
npm test -- --watch=false
npm run build
```

Use `npm ci` instead of `npm install` in CI or when reproducing the lockfile exactly.

The development server reads `proxy.conf.json` and forwards `/api` and `/actuator` to `http://127.0.0.1:8080`. Start Spring Boot first. Production deployments should serve Angular and route these paths to the backend at the reverse proxy; do not hard-code a developer tunnel hostname into source.

Typography intentionally uses medium or regular weights. Keep labels compact, allow values to wrap, and test narrow layouts whenever a field or timestamp is added.

## Payment boundaries

`GmoCardTokenService` is the only frontend service allowed to see card form values. In live mode it loads GMO's configured MP Token library and returns a one-use token; `CheckoutApiService` sends that token, never PAN/CVC, to Spring Boot. Other method panels collect only the provider-contract fields needed to begin a redirect or form-post journey.

The backend is authoritative for method eligibility and financial state. Angular follows `nextAction`, reloads a returned transaction, and presents its result; it must not infer success from a browser URL or callback field.

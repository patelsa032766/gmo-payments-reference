# Angular Developer Guide

This Angular 22.1 application implements the approved customer and operator shell. Each area is a lazy-loaded standalone route:

- `/checkout`: customer checkout; currently reads server-side eligible methods and performs no financial call
- `/configuration`: published method rules; draft/edit/publish commands are the next slice
- `/operations`: transaction timeline and request/response inspector foundation
- `/mit`: saved-instrument charge and execution form foundation

Shared API types and HTTP access live in `src/app/core/api`. Feature components must not construct backend URLs or call GMO directly. The backend owns credentials, eligibility, idempotency, and all financial decisions.

## Commands

Use Node.js 24 and npm 11:

```bash
npm install
npm start
npm test -- --watch=false
npm run build
```

The development server reads `proxy.conf.json` and forwards `/api` and `/actuator` to `http://127.0.0.1:8080`. Start Spring Boot first. Production deployments should serve Angular and route these paths to the backend at the reverse proxy; do not hard-code a developer tunnel hostname into source.

Typography intentionally uses medium or regular weights. Keep labels compact, allow values to wrap, and test narrow layouts whenever a field or timestamp is added.

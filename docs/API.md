# Foundation REST API

All application endpoints use the `/api/v1` prefix. The foundation exposes only safe reads; there is no GMO or financial command endpoint yet.

## Checkout options

```http
GET /api/v1/checkout/options?channel=PA&amountJpy=10000&monthly=true&ekycVerified=true&language=en
```

Parameters:

| Parameter | Values | Purpose |
| --- | --- | --- |
| `channel` | `PA`, `IA`, `FI` | Distribution channel eligibility |
| `amountJpy` | Positive integer | Applies method amount thresholds |
| `monthly` | Boolean | Recurring policies show recurring-capable methods only |
| `ekycVerified` | Boolean | Applies the special real-time bank-debit ceiling |
| `language` | `en`, `ja` | Selects server-owned display copy |

The response contains the configuration version and methods already filtered and ordered. Angular must not reimplement these rules.

```json
{
  "configurationVersion": 1,
  "methods": [
    {
      "code": "card",
      "label": "Credit or debit card",
      "description": "Visa, Mastercard, JCB, and American Express",
      "recurring": true,
      "displayOrder": 1
    }
  ]
}
```

## Active configuration

```http
GET /api/v1/configuration/active
```

Returns published release metadata and every configured method, including disabled methods. This is an operator read model; customer Checkout must use the options endpoint instead.

## Errors and health

Invalid inputs use RFC Problem Details. Unhandled failures receive the framework's sanitized error response; provider payloads and secrets must never be copied into a client-facing detail field.

Actuator health is available at:

```http
GET /actuator/health
```

Database health is part of the aggregate. A failed SQLite connection reports `DOWN` rather than allowing the process to appear healthy.

## Planned command conventions

Future payment, configuration publish, webhook replay, reconciliation, and MIT commands will require:

- an authenticated and authorized actor where applicable;
- an idempotency key and request fingerprint;
- a correlation ID returned to the caller;
- optimistic concurrency for mutable operator resources;
- `202 Accepted` for asynchronous/unknown outcomes rather than a false success;
- durable transaction/event linkage before a response is considered complete.

# REST API Guide

Application endpoints use `/api/v1`. Webhook endpoints intentionally live at `/webhooks/gmo` because GMO calls them directly. JSON errors use RFC Problem Details and never echo provider secrets or unsanitized payloads.

## Command conventions

- Customer payment and MIT commands require a stable `Idempotency-Key` header.
- Operator mutations require `X-Operator-Token`; optionally supply `X-Operator-Id` for audit attribution.
- Repeating an idempotency key with the same fingerprint returns the original result. Reusing it for different input is rejected.
- Enum-like payment method input uses public values: `card`, `paypay`, `bankDirect`, `kozaFurikae`, `kombini`, `payeasy`, and `furikomi`.
- Timestamps are UTC ISO-8601. Angular renders the operator-selected time zone.
- Card commands accept an MP token only. Raw PAN/CVC field names are rejected defensively.

## Checkout eligibility

```http
GET /api/v1/checkout/options?channel=PA&amountJpy=10000&monthly=true&ekycVerified=true&language=en
```

| Parameter | Values | Effect |
| --- | --- | --- |
| `channel` | `PA`, `IA`, `FI` | Distribution-channel rule |
| `amountJpy` | Positive integer | Minimum/maximum thresholds |
| `monthly` | Boolean | Monthly plans receive recurring-capable methods only |
| `ekycVerified` | Boolean | Real-time bank-debit eligibility ceiling |
| `language` | `en`, `ja` | Published customer label and description |

The backend returns only eligible methods, already ordered. Checkout must not duplicate policy logic.

## Browser payment configuration

```http
GET /api/v1/checkout/browser-configuration
```

Returns safe browser values such as simulation/live mode, MP Token script URL, and public Shop ID where needed. Passwords are never returned.

## Submit a checkout payment

```http
POST /api/v1/checkout/applications/APP-20260821-001/payments
Idempotency-Key: 4a2910e8-40fc-41aa-a913-4be2d9383ad1
Content-Type: application/json

{
  "method": "card",
  "details": {
    "token": "browser-generated-mp-token",
    "holderName": "AIKO TANAKA",
    "authorizationMode": "AUTH"
  }
}
```

The response includes `transactionId`, canonical `state`, `method`, `requiresAttention`, optional `nextAction`, and method-specific instructions. `REDIRECT` or `FORM_POST` means the browser must complete provider registration. The customer then returns through the configured callback and Angular reloads the result with:

```http
GET /api/v1/checkout/payments/{transactionId}
```

### Method detail fields

| Method | Important details | Result model |
| --- | --- | --- |
| Card | `token`, `holderName`, `authorizationMode=AUTH` | Authorize, then store via successful charge reference |
| PayPay | No sensitive account input | Recurring consent redirect, inquiry, first AUTH |
| Real-time bank debit | Bank/account registration fields required by enabled contract | Registration form post, inquiry, immediate debit |
| Koza Furikae | Registration bank fields | Registration form post, inquiry, then first-premium Furikomi instructions |
| Kombini | Customer/contact and store code | Instructions issued |
| Pay-easy | Customer/contact fields | Instructions issued |
| Furikomi | Customer/contact fields | Bank-transfer instructions issued |

The actual field matrix varies by the GMO product contract and supported bank. Validate the enabled sandbox product documentation before live mode.

## Configuration workspace

```http
GET    /api/v1/configuration/active
GET    /api/v1/configuration/workspace
PUT    /api/v1/configuration/draft
POST   /api/v1/configuration/draft/publish
DELETE /api/v1/configuration/draft
```

Write commands require `X-Operator-Token`. A draft request contains the complete ordered method collection; each item includes `code`, `enabled`, `recurring`, `monthlyOnly`, `minimumAmountJpy`, `maximumAmountJpy`, `nonEkycMaximumAmountJpy`, channels, labels/descriptions, and display order. Publishing is atomic: the previous release retires and the draft becomes the one published release.

## Operator transaction threads

```http
GET /api/v1/operations/transactions
GET /api/v1/operations/transactions/{transactionId}
```

The list returns current projections. The detail response returns the root transaction, ordered events, and sanitized provider exchanges. Every later refund, chargeback, webhook, inquiry, retry, browser return, and SFTP match is appended to the same root thread instead of appearing as an unrelated payment.

## Stored instruments and preferences

```http
GET /api/v1/mit/instruments
```

```http
PUT /api/v1/mit/customers/CUST-10042/preferences
X-Operator-Token: local-operator-token
Content-Type: application/json

{
  "primaryInstrumentId": "PI-CARD-10042",
  "backupInstrumentId": "PI-PAYPAY-10042"
}
```

There must be one active Primary and at most one different active Backup. SQLite partial unique indexes enforce the invariant as well as application validation. A backup may be omitted.

## Individual MIT

```http
POST /api/v1/mit/payments
X-Operator-Token: local-operator-token
Idempotency-Key: 9bcc5fd7-c770-4183-af15-ea82d984541a
Content-Type: application/json

{
  "instrumentId": "PI-CARD-10042",
  "amountJpy": 12500,
  "merchantReference": "PREMIUM-2026-09-10042",
  "details": { "authorizationMode": "CAPTURE" }
}
```

Card and PayPay support `AUTH` or `CAPTURE` where the provider contract permits. Real-time bank debit performs an individual debit. Koza instruments are intentionally rejected here and must use the batch endpoint.

## Monthly Koza batch

```http
POST /api/v1/mit/koza-batches
X-Operator-Token: local-operator-token
X-Operator-Id: payment-operator
Content-Type: application/json

{
  "batchReference": "KOZA-2026-09",
  "cycleYear": 2026,
  "cycleMonth": 9,
  "targetDate": "27",
  "submissionCutoffAt": "2026-09-20T08:00:00Z",
  "expectedResultDate": "2026-09-29",
  "items": [
    { "instrumentId": "PI-KOZA-10042", "amountJpy": 10000 }
  ]
}
```

The command creates one batch and one root payment transaction per item. Provider acceptance means scheduled/processing, not paid. Later asynchronous GMO notifications or SFTP rows transition each original item thread.

## Webhooks

OpenAPI JSON:

```http
POST /webhooks/gmo/openapi
X-Webhook-Ingress-Token: injected-by-trusted-edge
Content-Type: application/json
```

Legacy protocol notification:

```http
POST /webhooks/gmo/protocol
X-Webhook-Ingress-Token: injected-by-trusted-edge
Content-Type: application/x-www-form-urlencoded
```

When disabled, the endpoints return 404. When enabled, bad ingress credentials return 401. Valid messages are sanitized, payload-hash deduplicated, durably stored, linked by provider/application reference, and acknowledged only after durable receipt. The protocol endpoint returns the literal body `0` on success.

Browser returns are separate:

- `POST /webhooks/gmo/protocol/return/bank-direct`
- `POST /webhooks/gmo/protocol/return/koza-furikae`
- `GET /api/v1/gmo/returns/paypay-registration?p={provider-envelope}`

They locate an existing reservation and invoke authoritative inquiry/continuation. The browser payload alone never marks a payment paid.

## SFTP import

```http
POST /api/v1/reconciliation/sftp/import
X-Operator-Token: local-operator-token
X-Operator-Id: payment-operator
```

Returns a poll report with discovered/imported/skipped/failed counts. If SFTP is disabled, no remote connection is attempted.

## Health

```http
GET /actuator/health
```

SQLite participates in aggregate health. Public production deployments should expose only the health detail appropriate to their environment.

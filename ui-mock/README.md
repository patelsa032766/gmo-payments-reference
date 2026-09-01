# GMO Checkout Experience Mock

Status: approved and locked for implementation as baseline `20260901-28`. See [`BASELINE_V28.md`](./BASELINE_V28.md) for scope, change rules, validation evidence, and runtime checksums.

This directory contains the static UX specification for the future Angular and Spring Boot reference application. It makes no GMO, webhook, SFTP, or financial requests.

The prototype now covers four separate workspaces:

- `Checkout`: the customer payment and confirmation journey.
- `Configuration`: eligibility, theme, method visibility/order, test outcomes, draft/publish lifecycle, and generic public integration addresses.
- `API & Webhooks`: executable simulated lifecycle actions, webhook setup, reconciliation timelines, role states, and SFTP import history.
- `MIT Transactions`: individual recurring charges plus an end-to-end monthly Koza Furikae Select batch, including per-debit request acceptance and asynchronous results.

Operational fixture data is explicitly labelled as prototype data. Actions complete only in browser memory and append sanitized evidence to the existing transaction thread; no network or financial request is made.

## Run it

```bash
python3 -m http.server 8081 --directory "/Users/Samir/Documents/GMOPaymentsJava/ui-mock"
```

Open:

```text
http://127.0.0.1:8081/?v=20260901-28
```

If port `8081` is already occupied, the existing mock server may still be running. Check it before starting another process:

```bash
lsof -nP -iTCP:8081 -sTCP:LISTEN
```

## Important boundaries

- Sensitive card inputs are placeholders for GMO-hosted browser fields.
- No PAN, CVC, bank credential, GMO credential, SFTP key, or tunnel credential belongs in this directory.
- The generic `https://payments.example.com` address is a placeholder.
- A real local Cloudflare/custom-domain address entered in Configuration is saved only in browser storage.
- The production application will also support a gitignored local configuration file, SQLite runtime configuration, and locked environment overrides.
- `ui-mock/SESSION_HANDOFF.md` is the detailed source of truth.

## Operations design

The operations experience groups information at the transaction level. Selecting a transaction opens a Stripe-inspired split view: the business lifecycle appears newest-first on the left, while a technical inspector on the right shows the selected event's paired outbound/inbound exchange. Request and response tabs make it possible to step through API calls, webhook acknowledgements, browser returns, SFTP records, and reconciliation results without mixing the customer-facing state with transport details.

All inspector payloads are sanitized fixtures. They intentionally show masked credentials and representative integration shapes rather than real GMO requests or responses.

Each transaction is a durable payment thread. Later refunds, reversals, chargebacks, dispute actions, webhook updates, and reconciliation decisions append to the original thread rather than appearing as unrelated transaction rows. A child resource keeps its own provider ID, while the timeline and inspector preserve its link to the root transaction. Search includes both root and linked-resource identifiers.

The MIT operator flow now shows its production confirmation state. `Review charge` opens a final summary with the exact GMO operation and a method-specific `Confirm and charge/authorize/debit` action. Clicking that action runs a clearly labelled, session-only simulation and never contacts GMO; the future Spring Boot implementation will bind the same action to its authenticated MIT endpoint.

The selected-instrument summary repeats the current execution mode and updates immediately when the operator switches between immediate payment and authorize/capture-later.

The MIT prototype can now be exercised end to end. Confirming a charge shows processing, creates a session-only simulated result, adds a linked transaction thread with outbound/inbound fixture events, and offers `View transaction`. Saved-method removal lists the affected references, requires destructive confirmation, removes them from the active selector for the browser session, and preserves payment history. Reloading the page restores the original fixtures.

## Combined Koza Furikae registration and first premium

For a recurring policy, Checkout now offers a distinct combined method named `Furikomi + Koza Furikae` / `初回銀行振込＋口座振替`. It is intentionally separate from real-time bank debit (`口座直結決済`). The customer selects it once, enters the registration details once, and uses one primary action.

The single customer action represents two ordered backend operations:

1. Start online Koza Furikae registration with `BankAccountEntry`, redirect through GMO/the selected bank, and confirm the registration result from the browser return, asynchronous registration notification, or `BankAccountTranResult` inquiry.
2. Only after registration is confirmed, create a one-time virtual account through the Furikomi `EntryTranVirtualaccount` / `ExecTranVirtualaccount` flow and render the first-premium transfer instructions.

This is one customer journey, but it must remain two linked domain operations. A successful registration does not mean the first premium is paid. The completion screen therefore shows `Koza Furikae registered` for future premiums and `Bank transfer due` for the first premium, including the amount, virtual account, exact transfer reference, and deadline. If registration is cancelled, fails, or remains unknown, no transfer account is issued and the customer stays on the payment page. Production should persist a combined enrollment workflow that links the durable mandate registration and first-payment transaction to the same application without storing raw bank credentials.

Official GMO references used for this customer flow:

- [Online bank-account registration flow](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/entry/online/entry-flow)
- [Online bank-account registration API list](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/entry/online/api-list)
- [BankAccountEntry request](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/bank-account-entry)
- [Furikomi virtual-account flow](https://docs.gmo-pg.com/mulpay/docs/payment-method/cash/virtualaccount/flow-protocol)

## Koza Furikae monthly batch

The MIT workspace deliberately separates two bank products:

| Product | Internal code for the build | Execution | Financial result |
| --- | --- | --- | --- |
| Real-time bank debit / `口座直結決済` | `bank_direct_realtime` | Individual checkout or MIT request | Immediate debit result |
| Koza Furikae Select / `口座振替（セレクト）` | `koza_furikae_select` | Scheduled monthly operator batch | Later asynchronous result |

The mock's monthly batch is an application-owned grouping, not a single GMO payment. Review and submission create one `EntryTranBankaccount` and `ExecTranBankaccount` pair, one Order ID, and one durable transaction thread for every included customer debit. A returned `REQSUCCESS` state means the request was accepted for processing; it is displayed as `Debit scheduled`, never `Paid`.

`Apply async results` then simulates two independent protocol result messages: `PAYSUCCESS` with debit-result code `0` and `PAYFAIL` with code `1` for insufficient funds. A third accepted request remains scheduled because no result message has arrived. Each received message is stored, acknowledged with HTTP 200/body `0`, and appended to its original transaction thread. The batch summary is derived from those item states.

The fixture uses GMO's September 2026 27th-cycle dates: request cutoff 10 September, actual bank debit 28 September, and result return 1 October. The production application must load or configure the applicable GMO calendar rather than hardcoding these dates.

Configuration independently controls `Koza Furikae registration + batches` and Koza asynchronous notifications. Turning off the first control removes the combined Checkout method and disables monthly batch submission. Turning off notifications leaves accepted requests pending; webhook-independent SFTP reconciliation remains available as a separate source.

Official GMO references used for the mock:

- [Koza Furikae Select processing flow](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/payment/select/flow-protocol)
- [Koza Furikae Select API list](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/payment/select/api-list)
- [ExecTranBankaccount request](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/exec-tran-bankaccount)
- [Koza Furikae result notification](https://docs.gmo-pg.com/mulpay/apis/protocol-type/idpass/bank-account-select-notification)
- [Debit result codes](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/payment/transfer-result)
- [Koza Furikae Select calendar](https://docs.gmo-pg.com/mulpay/docs/payment-method/account/accounttrans/calendar-select)

The final pre-build pass adds the operational states that determine the production domain model:

- Checkout can simulate a decline, customer cancellation, registered-bank/debit failure, or unknown result. An unknown result blocks a second request and exposes a safe status inquiry before confirmation.
- MIT can simulate success, decline, or timeout/unknown outcome. Unknown outcomes are marked for inquiry before retry.
- Transaction detail completes inquiry, safe retry, authorization capture, void, partial/full refund, discrepancy resolution, and dispute-review recording. Every result is appended to the original thread with operator and timestamp evidence.
- Source conflicts can be resolved to a canonical state with an operator note while retaining the original API, webhook, inquiry, and SFTP evidence.
- Payment operator, configuration administrator, and read-only auditor states demonstrate action permissions; auditor financial controls are disabled.
- Configuration changes become a draft preview. Operators may discard them or publish a new active version with publisher and timestamp metadata.

Each customer with saved methods has exactly one Primary preference and may have one different Backup. The most recently registered fixture method becomes Primary by default. Roles appear in the saved-method selector, selected-instrument facts, and right-side payment-preference summary. `Manage` changes the session-only marking; the charge simulator does not yet select or fall back between methods automatically.

Timestamps use a city-based selector backed by IANA time zones. The UI displays `Tokyo`, `London`, `New York`, and similar city labels rather than raw `GMT+/-` choices.

SFTP remains independent of webhook enablement. Fixtures illustrate daily transaction-file imports, `.zip.ok` readiness markers, checksums, deduplication, rejected rows, conflicts, and remote cleanup state.

## Validation

```bash
node --check /Users/Samir/Documents/GMOPaymentsJava/ui-mock/app.js
node --check /Users/Samir/Documents/GMOPaymentsJava/ui-mock/ops-mock.js
```

Review desktop and mobile widths after any visual change. Advance the cache version on all CSS and JavaScript references in `index.html` when assets change.

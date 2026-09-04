import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { BrowserPaymentConfiguration, CheckoutApiService, PaymentMethodOption, PaymentSubmission } from '../../core/api/checkout-api.service';
import { GmoCardTokenService } from '../../core/api/gmo-card-token.service';
import { PaymentMethodDetailsComponent } from './payment-method-details/payment-method-details.component';

/**
 * Customer-facing checkout entry point.
 *
 * Visible methods come from the server-side eligibility policy and each expanded
 * method delegates payment-specific fields to a child component. Card PAN/CVC
 * values are exchanged for a one-use GMO token in the browser; raw credentials
 * and card data are never submitted to this application's backend.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, PaymentMethodDetailsComponent],
  selector: 'app-checkout-page',
  styleUrl: './checkout.page.scss',
  templateUrl: './checkout.page.html',
})
export class CheckoutPage implements OnInit {
  private readonly api = inject(CheckoutApiService);
  private readonly cardTokens = inject(GmoCardTokenService);

  protected readonly amountJpy = 10_000;
  protected readonly methods = signal<PaymentMethodOption[]>([]);
  protected readonly selectedCode = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly warning = signal<string | null>(null);
  protected readonly confirmed = signal(false);
  protected readonly submitting = signal(false);
  protected readonly configurationVersion = signal<number | null>(null);
  protected readonly browserConfiguration = signal<BrowserPaymentConfiguration | null>(null);
  protected readonly submission = signal<PaymentSubmission | null>(null);
  protected readonly paymentDetails = signal<Record<string, unknown>>({});
  private idempotencyKey = crypto.randomUUID();

  ngOnInit(): void {
    this.loadMethods();
    const returnedTransaction = new URLSearchParams(window.location.search).get('paymentReturn');
    if (returnedTransaction) this.restoreReturnedPayment(returnedTransaction);
  }

  protected select(code: string): void {
    this.selectedCode.set(code);
    this.error.set(null);
    this.warning.set(null);
    this.paymentDetails.set({});
    this.idempotencyKey = crypto.randomUUID();
  }

  protected async continue(): Promise<void> {
    if (!this.selectedCode()) {
      this.error.set('Choose a payment method to continue.');
      return;
    }
    const method = this.selectedCode()!;
    const configuration = this.browserConfiguration();
    if (!configuration) { this.error.set('Payment security configuration is still loading.'); return; }
    this.submitting.set(true); this.error.set(null);
    try {
      let details = { ...this.paymentDetails() };
      if (method === 'card') {
        const token = await this.cardTokens.tokenize(configuration, details);
        details = { token: token.token, holderName: token.holderName,
          authorizationMode: this.selectedMethod()?.citExecutionMode ?? 'AUTH' };
      }
      this.api.submitPayment('APP-20260821-001', method, details, this.idempotencyKey).subscribe({
        next: result => { this.submission.set(result); this.submitting.set(false); this.follow(result); },
        error: response => { this.submitting.set(false); this.error.set(response?.error?.detail ?? response?.error?.message ?? 'Payment could not be completed. Check the details and try again.'); },
      });
    } catch (error) {
      this.submitting.set(false);
      this.error.set(error instanceof Error ? error.message : 'Payment could not be prepared.');
    }
  }

  protected updateDetails(details: Record<string, unknown>): void { this.paymentDetails.set(details); }

  protected startAgain(): void {
    this.confirmed.set(false);
    this.selectedCode.set(null);
    this.submission.set(null);
    this.warning.set(null);
    this.idempotencyKey = crypto.randomUUID();
  }

  protected selectedMethod(): PaymentMethodOption | undefined {
    return this.methods().find((method) => method.code === this.selectedCode());
  }

  protected methodMonogram(method: PaymentMethodOption): string {
    const monograms: Record<string, string> = {
      card: 'CARD', paypay: 'PayPay', bankDirect: 'BANK', kozaFurikae: '口座',
      kombini: 'STORE', payeasy: 'ATM', furikomi: 'BANK',
    };
    return monograms[method.code] ?? method.code.slice(0, 6).toUpperCase();
  }

  private loadMethods(): void {
    this.loading.set(true);
    this.api.getBrowserConfiguration().subscribe({
      next: configuration => {
        this.browserConfiguration.set(configuration);
        // Flask's known-good integration initializes GMO as the page loads.
        // Prewarming here gives MpToken.js device-fingerprint setup time to
        // settle before the customer submits the card form. tokenize() still
        // initializes defensively if this best-effort preload fails.
        void this.cardTokens.initialize(configuration).catch(() => undefined);
      },
      error: () => this.error.set('Payment security configuration could not be loaded.'),
    });
    this.api.getOptions({ channel: 'PA', amountJpy: this.amountJpy, monthly: true, ekycVerified: true, language: 'en' })
      .subscribe({
        next: (response) => {
          this.methods.set(response.methods);
          this.configurationVersion.set(response.configurationVersion);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Payment methods could not be loaded. Please try again.');
          this.loading.set(false);
        },
      });
  }

  private follow(result: PaymentSubmission): void {
    const action = result.nextAction;
    if (action?.type === 'REDIRECT' && action.url) { window.location.assign(action.url); return; }
    if (action?.type === 'FORM_POST' && action.url) {
      const form = document.createElement('form'); form.method = 'POST'; form.action = action.url;
      Object.entries(action.fields ?? {}).forEach(([name, value]) => {
        const input = document.createElement('input'); input.type = 'hidden'; input.name = name; input.value = value; form.appendChild(input);
      });
      document.body.appendChild(form); form.submit(); return;
    }
    if (result.requiresAttention && this.isCustomerSuccess(result.state)) {
      // A conclusive first payment must never be presented as failed merely
      // because reusable-method setup needs operator follow-up. Retrying the
      // financial step could create a duplicate authorization.
      this.warning.set('Your payment completed. We may contact you if the future payment method needs attention.');
      this.confirmed.set(true);
      return;
    }
    if (result.requiresAttention || ['FAILED', 'UNKNOWN'].includes(result.state)) {
      this.error.set('The payment was not completed. Please check the details or choose another method.');
      // A conclusive provider rejection may be retried by the customer, but a
      // retry must be a new payment command with a fresh one-use GMO token and
      // a fresh application idempotency key. UNKNOWN outcomes deliberately do
      // not get an automatic retry path because they require inquiry first.
      if (result.state === 'FAILED') this.idempotencyKey = crypto.randomUUID();
      return;
    }
    this.confirmed.set(true);
  }

  private restoreReturnedPayment(transactionId: string): void {
    this.submitting.set(true);
    this.api.getPayment(transactionId).subscribe({
      next: result => {
        this.submitting.set(false);
        this.submission.set(result);
        // Submission responses use the domain enum name while checkout options use
        // the stable public API value. Normalize browser-return responses so the
        // confirmation screen can recover the correct customer-facing label.
        this.selectedCode.set(this.apiMethodCode(result.method));
        if (result.requiresAttention && this.isCustomerSuccess(result.state)) {
          this.warning.set('Your payment completed. We may contact you if the future payment method needs attention.');
          this.confirmed.set(true);
          return;
        }
        if (result.requiresAttention || ['FAILED', 'UNKNOWN', 'REGISTRATION_PENDING'].includes(result.state)) {
          this.error.set(result.state === 'REGISTRATION_PENDING'
            ? 'The bank registration is still pending. Please wait or contact support before trying again.'
            : 'The payment was not completed. Please check the details or choose another method.');
          return;
        }
        this.confirmed.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('The returned payment result could not be loaded. Please contact support.');
      },
    });
  }

  private apiMethodCode(value: string): string {
    const enumToApi: Record<string, string> = {
      CARD: 'card',
      PAYPAY: 'paypay',
      BANK_DIRECT_REALTIME: 'bankDirect',
      KOZA_FURIKAE_SELECT: 'kozaFurikae',
      KOMBINI: 'kombini',
      PAYEASY: 'payeasy',
      FURIKOMI: 'furikomi',
    };
    return enumToApi[value] ?? value;
  }

  private isCustomerSuccess(state: string): boolean {
    return ['AUTHORIZED', 'PAID', 'INSTRUCTIONS_ISSUED', 'MANDATE_REGISTERED_TRANSFER_DUE'].includes(state);
  }
}

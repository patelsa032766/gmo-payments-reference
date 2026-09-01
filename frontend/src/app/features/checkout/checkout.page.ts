import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { CheckoutApiService, PaymentMethodOption } from '../../core/api/checkout-api.service';

/**
 * Customer-facing checkout entry point.
 *
 * This first vertical slice proves that visible methods come from the server-side
 * eligibility policy. Payment collection will be added behind method-specific
 * adapters; no component will ever receive raw GMO credentials.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe],
  selector: 'app-checkout-page',
  styleUrl: './checkout.page.scss',
  templateUrl: './checkout.page.html',
})
export class CheckoutPage implements OnInit {
  private readonly api = inject(CheckoutApiService);

  protected readonly amountJpy = 10_000;
  protected readonly methods = signal<PaymentMethodOption[]>([]);
  protected readonly selectedCode = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly confirmed = signal(false);
  protected readonly configurationVersion = signal<number | null>(null);

  ngOnInit(): void { this.loadMethods(); }

  protected select(code: string): void {
    this.selectedCode.set(code);
    this.error.set(null);
  }

  protected continue(): void {
    if (!this.selectedCode()) {
      this.error.set('Choose a payment method to continue.');
      return;
    }
    this.confirmed.set(true);
  }

  protected startAgain(): void {
    this.confirmed.set(false);
    this.selectedCode.set(null);
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
}

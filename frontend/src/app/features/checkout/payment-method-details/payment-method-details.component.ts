import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { PaymentMethodOption } from '../../../core/api/checkout-api.service';

interface MethodPresentation {
  title: string;
  description: string;
  badge: string;
  note: string;
}

const PRESENTATION: Record<string, MethodPresentation> = {
  card: {
    title: 'Secure card details', description: 'Enter your card details to authorize the first payment.',
    badge: 'Secure payment', note: 'Your card details are handled securely by GMO and are not stored by us.',
  },
  paypay: {
    title: 'Continue with PayPay', description: 'We securely redirect you to PayPay to approve this payment.',
    badge: 'Secure handoff', note: 'Your PayPay sign-in and approval are completed securely on PayPay.',
  },
  bankDirect: {
    title: 'Authorize a bank account', description: 'Register a supported bank account and debit today in real time.',
    badge: 'Secure registration', note: 'Your account details are handled securely by GMO and your bank.',
  },
  kozaFurikae: {
    title: 'Set up monthly bank debit', description: 'Register future monthly debit, then receive first-premium transfer instructions.',
    badge: 'Secure registration', note: 'The first premium uses Furikomi; later premiums use monthly Koza Furikae.',
  },
  kombini: {
    title: 'Convenience-store instructions', description: 'Choose a store and receive the numbers needed for this payment.',
    badge: 'Instructions', note: 'The customer completes payment at the selected convenience store.',
  },
  payeasy: {
    title: 'Pay-easy instructions', description: 'Receive the numbers needed at an ATM or through online banking.',
    badge: 'Instructions', note: 'The customer completes payment through an eligible ATM or online bank.',
  },
  furikomi: {
    title: 'Bank-transfer account', description: 'Create a one-time virtual account for this payment.',
    badge: 'Instructions', note: 'The customer transfers funds from their own bank account.',
  },
};

/**
 * Provider-specific customer details for every catalog method, including methods
 * currently hidden by eligibility. Keeping this exhaustive means a configuration
 * enablement change cannot expose an empty accordion.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet],
  selector: 'app-payment-method-details',
  styleUrl: './payment-method-details.component.scss',
  templateUrl: './payment-method-details.component.html',
})
export class PaymentMethodDetailsComponent {
  readonly method = input.required<PaymentMethodOption>();
  readonly amountJpy = input.required<number>();

  protected readonly deliveryOptions = ['EMAIL', 'LINE', 'SMS'] as const;
  protected readonly delivery = signal<'EMAIL' | 'LINE' | 'SMS'>('EMAIL');
  protected readonly instructionsIssued = signal(false);
  protected readonly presentation = computed(() => PRESENTATION[this.method().code] ?? {
    title: this.method().label,
    description: this.method().description,
    badge: 'Payment',
    note: 'Continue to complete this payment method.',
  });

  protected chooseDelivery(delivery: 'EMAIL' | 'LINE' | 'SMS'): void {
    this.delivery.set(delivery);
  }

  protected issueInstructions(): void {
    this.instructionsIssued.set(true);
  }
}

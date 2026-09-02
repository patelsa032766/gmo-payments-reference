import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PaymentMethodOption } from '../../../core/api/checkout-api.service';
import { PaymentMethodDetailsComponent } from './payment-method-details.component';

/**
 * The eligibility API normally omits disabled methods from checkout. This test
 * deliberately renders every catalog code—including methods disabled in the
 * active configuration—to guarantee that enabling one never produces an empty
 * accordion while a frontend deployment catches up.
 */
describe('PaymentMethodDetailsComponent', () => {
  const catalog: ReadonlyArray<{ code: string; expectedTitle: string }> = [
    { code: 'card', expectedTitle: 'Secure card details' },
    { code: 'paypay', expectedTitle: 'Continue with PayPay' },
    { code: 'bankDirect', expectedTitle: 'Authorize a bank account' },
    { code: 'kozaFurikae', expectedTitle: 'Set up monthly bank debit' },
    { code: 'kombini', expectedTitle: 'Convenience-store instructions' },
    { code: 'payeasy', expectedTitle: 'Pay-easy instructions' },
    { code: 'furikomi', expectedTitle: 'Bank-transfer account' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentMethodDetailsComponent],
    }).compileComponents();
  });

  for (const entry of catalog) {
    it(`renders complete details for ${entry.code}`, () => {
      const fixture: ComponentFixture<PaymentMethodDetailsComponent> =
        TestBed.createComponent(PaymentMethodDetailsComponent);
      const method: PaymentMethodOption = {
        code: entry.code,
        label: entry.code,
        description: `${entry.code} description`,
        recurring: true,
        displayOrder: 1,
      };

      fixture.componentRef.setInput('method', method);
      fixture.componentRef.setInput('amountJpy', 10_000);
      fixture.detectChanges();

      const details = fixture.nativeElement.querySelector(`#method-details-${entry.code}`);
      expect(details).not.toBeNull();
      expect(details.textContent).toContain(entry.expectedTitle);
      fixture.destroy();
    });
  }
});

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
        citExecutionMode: 'AUTH',
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

  it('emits an empty PayPay payload rather than leaking card-form keys', async () => {
    const fixture = TestBed.createComponent(PaymentMethodDetailsComponent);
    const emitted: Record<string, unknown>[] = [];
    fixture.componentInstance.detailsChange.subscribe(value => emitted.push(value));
    fixture.componentRef.setInput('method', {
      code: 'paypay', label: 'PayPay', description: 'PayPay approval', recurring: true,
      displayOrder: 1, citExecutionMode: 'AUTH',
    } satisfies PaymentMethodOption);
    fixture.componentRef.setInput('amountJpy', 10_000);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(emitted.at(-1)).toEqual({});
    fixture.destroy();
  });

  it('prefills the provider-compatible Kana name for a known bank-direct customer', async () => {
    const fixture = TestBed.createComponent(PaymentMethodDetailsComponent);
    const emitted: Record<string, unknown>[] = [];
    fixture.componentInstance.detailsChange.subscribe(value => emitted.push(value));
    fixture.componentRef.setInput('method', {
      code: 'bankDirect', label: 'Bank Direct', description: 'Bank registration', recurring: true,
      displayOrder: 1, citExecutionMode: 'AUTH',
    } satisfies PaymentMethodOption);
    fixture.componentRef.setInput('amountJpy', 10_000);
    fixture.componentRef.setInput('customerCode', 'CUST-10042');
    fixture.detectChanges();
    await fixture.whenStable();

    const nameInput = fixture.nativeElement.querySelector('input[name="rtName"]') as HTMLInputElement;
    expect(nameInput.value).toBe('アイコ　タナカ');
    expect(emitted.at(-1)?.['accountNameKana']).toBe('アイコ　タナカ');
    expect(fixture.nativeElement.querySelector('input[name="rtNumber"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('select[name="rtType"]')).toBeNull();
    expect(emitted.at(-1)).toEqual({ bankCode: '0001', accountNameKana: 'アイコ　タナカ' });
    fixture.destroy();
  });
});

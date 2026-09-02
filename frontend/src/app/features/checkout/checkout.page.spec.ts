import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CheckoutApiService } from '../../core/api/checkout-api.service';
import { CheckoutPage } from './checkout.page';

describe('CheckoutPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutPage],
      providers: [{
        provide: CheckoutApiService,
        useValue: {
          getOptions: () => of({
            configurationVersion: 1,
            methods: [{
              code: 'card',
              label: 'Credit or debit card',
              description: 'Visa, Mastercard, JCB, and American Express',
              recurring: true,
              displayOrder: 1,
            }],
          }),
          getBrowserConfiguration: () => of({
            liveCallsEnabled: false, webhooksEnabled: false,
            mpTokenJsUrl: '', shopId: '', publicBaseUrl: 'http://localhost:8080',
            openapiWebhookUrl: null, protocolNotificationUrl: null,
          }),
        },
      }],
    }).compileComponents();
  });

  it('expands the secure card fields when Card is selected', () => {
    const fixture = TestBed.createComponent(CheckoutPage);
    fixture.detectChanges();

    const cardButton = fixture.nativeElement.querySelector('.method-card-trigger') as HTMLButtonElement;
    expect(cardButton.getAttribute('aria-expanded')).toBe('false');

    cardButton.click();
    fixture.detectChanges();

    expect(cardButton.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('#method-details-card')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Secure card details');
  });
});

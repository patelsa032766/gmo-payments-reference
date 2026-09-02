import { TestBed } from '@angular/core/testing';
import { BrowserPaymentConfiguration } from './checkout-api.service';
import { GmoCardTokenService } from './gmo-card-token.service';

describe('GmoCardTokenService', () => {
  const configuration: BrowserPaymentConfiguration = {
    liveCallsEnabled: true,
    webhooksEnabled: false,
    mpTokenJsUrl: 'https://example.test/mp-token.js',
    shopId: 'test-shop',
    publicBaseUrl: 'http://localhost:8080',
    openapiWebhookUrl: null,
    protocolNotificationUrl: null,
  };

  afterEach(() => {
    delete window.Multipayment;
  });

  it('uses the token client that GMO installs globally during init', async () => {
    const service = TestBed.inject(GmoCardTokenService);
    // Loading the remote script is outside this unit test; its two-stage API
    // shape is reproduced below so this regression remains deterministic.
    (service as unknown as { load: (url: string) => Promise<void> }).load = async () => undefined;

    window.Multipayment = {
      init: () => {
        window.Multipayment = {
          getToken: (_card, callback) => callback({
            resultCode: '000',
            tokenObject: { token: 'sandbox-token' },
          }),
        };
      },
    };

    const result = await service.tokenize(configuration, {
      cardNumber: '4111111111111111',
      expiry: '1229',
      securityCode: '123',
      holderName: 'Taro Mihon',
    });

    expect(result).toEqual({ token: 'sandbox-token', holderName: 'TARO MIHON' });
  });

  it('reuses an already initialized token client for a customer retry', async () => {
    const service = TestBed.inject(GmoCardTokenService);
    (service as unknown as { load: (url: string) => Promise<void> }).load = async () => undefined;
    window.Multipayment = {
      getToken: (_card, callback) => callback({
        resultCode: '000',
        tokenObject: { token: '  replacement-sandbox-token  ' },
      }),
    };

    const result = await service.tokenize(configuration, {
      cardNumber: '4111111111111111',
      expiry: '1229',
      securityCode: '123',
      holderName: 'Taro Mihon',
    });

    expect(result.token).toBe('replacement-sandbox-token');
  });
});

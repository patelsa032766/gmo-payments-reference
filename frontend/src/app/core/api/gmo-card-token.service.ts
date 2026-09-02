import { Injectable } from '@angular/core';
import { BrowserPaymentConfiguration } from './checkout-api.service';

interface MultipaymentBootstrapApi {
  init(shopId: string): void;
}

interface MultipaymentTokenApi {
  getToken(card: Record<string, string>, callback: MultipaymentTokenCallback): void;
}

type MultipaymentTokenResponse = { resultCode: string; tokenObject?: { token: string } };
type MultipaymentTokenCallback = (response: MultipaymentTokenResponse) => void;

declare global {
  interface Window {
    Multipayment?: MultipaymentBootstrapApi | MultipaymentTokenApi;
  }
}

/**
 * Browser-only GMO MP-token bridge. PAN and security code are sent directly
 * from the customer's browser to GMO's script and are never included in a
 * Spring request, application log, or SQLite row.
 */
@Injectable({ providedIn: 'root' })
export class GmoCardTokenService {
  private loadedUrl: string | null = null;

  async tokenize(configuration: BrowserPaymentConfiguration,
                 card: Record<string, unknown>): Promise<{ token: string; holderName: string }> {
    if (!configuration.liveCallsEnabled) {
      return { token: 'SIMULATED_MP_TOKEN', holderName: String(card['holderName'] ?? 'A TEST CUSTOMER') };
    }
    if (!configuration.shopId || !configuration.mpTokenJsUrl) {
      throw new Error('Card tokenization is not configured for this environment.');
    }
    await this.load(configuration.mpTokenJsUrl);
    const loadedApi = window.Multipayment;
    if (!loadedApi) {
      throw new Error('GMO card tokenization did not initialize.');
    }

    /*
     * GMO's browser library deliberately changes the global object during
     * initialization. Before init(), window.Multipayment exposes only init().
     * The init() call then replaces that global with the client exposing
     * getToken(). Re-reading the global is therefore required; retaining the
     * bootstrap object would make api.getToken undefined.
     */
    if ('init' in loadedApi) loadedApi.init(configuration.shopId);
    const tokenApi = 'getToken' in loadedApi ? loadedApi : window.Multipayment;
    if (!tokenApi || !('getToken' in tokenApi)) {
      throw new Error('The GMO card security library did not expose its tokenization client.');
    }
    const number = String(card['cardNumber'] ?? '').replace(/\D/g, '');
    const expiry = String(card['expiry'] ?? '').replace(/\D/g, '');
    const securityCode = String(card['securityCode'] ?? '').replace(/\D/g, '');
    const holderName = String(card['holderName'] ?? '').trim().toUpperCase();
    if (number.length < 12 || expiry.length !== 4 || securityCode.length < 3 || !holderName) {
      throw new Error('Enter complete card details before continuing.');
    }
    const expire = `20${expiry.slice(2)}${expiry.slice(0, 2)}`;
    return new Promise((resolve, reject) => {
      // GMO documents that the legacy getToken callback must be named. Keep
      // this declaration rather than replacing it with an anonymous closure.
      function handleGmoTokenResult(response: MultipaymentTokenResponse): void {
        const token = response.tokenObject?.token?.trim();
        if (response.resultCode !== '000' || !token) {
          reject(new Error('GMO could not tokenize this card. Check the details and try again.'));
        } else resolve({ token, holderName });
      }
      tokenApi.getToken({ cardno: number, expire, securitycode: securityCode,
        holdername: holderName, tokennumber: '1' }, handleGmoTokenResult);
    });
  }

  private load(url: string): Promise<void> {
    if (this.loadedUrl === url && window.Multipayment) return Promise.resolve();
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = url; script.async = true;
      script.onload = () => { this.loadedUrl = url; resolve(); };
      script.onerror = () => reject(new Error('The GMO card security library could not be loaded.'));
      document.head.appendChild(script);
    });
  }
}

import { Injectable } from '@angular/core';
import { BrowserPaymentConfiguration } from './checkout-api.service';

interface MultipaymentApi {
  init(shopId: string): void;
  getToken(card: Record<string, string>, callback: (response: { resultCode: string; tokenObject?: { token: string } }) => void): void;
}

declare global { interface Window { Multipayment?: MultipaymentApi; } }

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
    const api = window.Multipayment;
    if (!api) throw new Error('GMO card tokenization did not initialize.');
    api.init(configuration.shopId);
    const number = String(card['cardNumber'] ?? '').replace(/\D/g, '');
    const expiry = String(card['expiry'] ?? '').replace(/\D/g, '');
    const securityCode = String(card['securityCode'] ?? '').replace(/\D/g, '');
    const holderName = String(card['holderName'] ?? '').trim().toUpperCase();
    if (number.length < 12 || expiry.length !== 4 || securityCode.length < 3 || !holderName) {
      throw new Error('Enter complete card details before continuing.');
    }
    const expire = `20${expiry.slice(2)}${expiry.slice(0, 2)}`;
    return new Promise((resolve, reject) => api.getToken({ cardno: number, expire,
      securitycode: securityCode, holdername: holderName, tokennumber: '1' }, response => {
      if (response.resultCode !== '000' || !response.tokenObject?.token) {
        reject(new Error('GMO could not tokenize this card. Check the details and try again.'));
      } else resolve({ token: response.tokenObject.token, holderName });
    }));
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

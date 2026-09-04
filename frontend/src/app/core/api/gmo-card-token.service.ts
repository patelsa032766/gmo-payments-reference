import { Injectable } from '@angular/core';
import { BrowserPaymentConfiguration } from './checkout-api.service';

interface MultipaymentBootstrapApi {
  init(shopId: string): void;
}

interface MultipaymentTokenApi {
  getToken(card: Record<string, string>, callback: MultipaymentTokenCallback):
    void | Promise<MultipaymentTokenResponse | void>;
}

type MultipaymentTokenResponse = {
  resultCode: string;
  tokenObject?: { token: string | string[] };
};
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
  private loading: Promise<void> | null = null;

  /**
   * Mirrors the proven Flask integration by loading and initializing GMO when
   * the checkout page starts, rather than racing SDK/device-fingerprint setup
   * against the customer's Continue click.
   */
  async initialize(configuration: BrowserPaymentConfiguration): Promise<void> {
    if (!configuration.liveCallsEnabled) return;
    await this.tokenClient(configuration);
  }

  async tokenize(configuration: BrowserPaymentConfiguration,
                 card: Record<string, unknown>): Promise<{ token: string; holderName: string }> {
    if (!configuration.liveCallsEnabled) {
      return { token: 'SIMULATED_MP_TOKEN', holderName: String(card['holderName'] ?? 'A TEST CUSTOMER') };
    }
    if (!configuration.shopId || !configuration.mpTokenJsUrl) {
      throw new Error('Card tokenization is not configured for this environment.');
    }
    const tokenApi = await this.tokenClient(configuration);
    const number = String(card['cardNumber'] ?? '').replace(/\D/g, '');
    const expiry = String(card['expiry'] ?? '').replace(/\D/g, '');
    const securityCode = String(card['securityCode'] ?? '').replace(/\D/g, '');
    const holderName = String(card['holderName'] ?? '').trim().toUpperCase();
    if (number.length < 12 || expiry.length !== 4 || securityCode.length < 3 || !holderName) {
      throw new Error('Enter complete card details before continuing.');
    }
    const expire = `20${expiry.slice(2)}${expiry.slice(0, 2)}`;
    return new Promise((resolve, reject) => {
      let settled = false;
      const timeout = window.setTimeout(() => {
        fail('GMO card tokenization timed out. Check the connection and try again.');
      }, 30_000);

      const fail = (message: string): void => {
        if (settled) return;
        settled = true;
        window.clearTimeout(timeout);
        reject(new Error(message));
      };

      const succeed = (token: string): void => {
        if (settled) return;
        settled = true;
        window.clearTimeout(timeout);
        resolve({ token, holderName });
      };

      // GMO documents that the legacy getToken callback must be named. Keep
      // this declaration rather than replacing it with an anonymous closure.
      function handleGmoTokenResult(response: MultipaymentTokenResponse): void {
        /*
         * Older MpToken.js releases returned tokenObject.token as one string.
         * The current V2 compatibility API returns an array when tokennumber
         * is supplied—even when tokennumber is "1". Accept both shapes so an
         * SDK update cannot turn the array into an invalid comma-coerced token
         * or throw while calling String.trim(). Only the requested first token
         * is forwarded; neither representation is logged or persisted.
         */
        const tokenValue = response.tokenObject?.token;
        const token = (Array.isArray(tokenValue) ? tokenValue[0] : tokenValue)?.trim();
        if (response.resultCode !== '000' || !token) {
          fail(`GMO could not tokenize this card (result ${response.resultCode}). Check the details and try again.`);
        } else succeed(token);
      }

      try {
        const pending = tokenApi.getToken({ cardno: number, expire, securitycode: securityCode,
          holdername: holderName, tokennumber: '1' }, handleGmoTokenResult);
        // Current MpToken.js returns a Promise in addition to supporting the
        // legacy callback. A network/CORS failure rejects that Promise without
        // invoking the callback, so it must be observed to prevent an endless
        // Processing state. Some compatible versions also resolve with the
        // result instead of calling back; accept both behaviors safely.
        if (pending && typeof pending.then === 'function') {
          pending.then(response => {
            if (response?.resultCode) handleGmoTokenResult(response);
          }).catch(() => fail('GMO card tokenization could not be reached. Please try again.'));
        }
      } catch {
        fail('GMO card tokenization could not be started. Please try again.');
      }
    });
  }

  private async tokenClient(configuration: BrowserPaymentConfiguration): Promise<MultipaymentTokenApi> {
    if (!configuration.shopId || !configuration.mpTokenJsUrl) {
      throw new Error('Card tokenization is not configured for this environment.');
    }
    await this.load(configuration.mpTokenJsUrl);
    const loadedApi = window.Multipayment;
    if (!loadedApi) throw new Error('GMO card tokenization did not initialize.');

    /*
     * GMO's browser library deliberately changes the global object during
     * initialization. Before init(), window.Multipayment exposes only init().
     * The init() call then replaces that global with the client exposing
     * getToken(). Re-reading the global is therefore required; retaining the
     * bootstrap object would make api.getToken undefined.
     */
    if ('init' in loadedApi) loadedApi.init(configuration.shopId);
    const initializedApi = 'getToken' in loadedApi ? loadedApi : window.Multipayment;
    if (!initializedApi || !('getToken' in initializedApi)) {
      throw new Error('The GMO card security library did not expose its tokenization client.');
    }
    return initializedApi;
  }

  private load(url: string): Promise<void> {
    if (this.loadedUrl === url && window.Multipayment) return Promise.resolve();
    if (this.loading) return this.loading;
    this.loading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = url; script.async = true;
      script.onload = () => { this.loadedUrl = url; this.loading = null; resolve(); };
      script.onerror = () => {
        this.loading = null;
        reject(new Error('The GMO card security library could not be loaded.'));
      };
      document.head.appendChild(script);
    });
    return this.loading;
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CheckoutLanguage = 'en' | 'ja';
export type DistributionChannel = 'PA' | 'IA' | 'FI';
export type PaymentExecutionMode = 'AUTH' | 'CAPTURE';
export interface PaymentMethodOption { code: string; label: string; description: string; recurring: boolean; displayOrder: number; citExecutionMode: PaymentExecutionMode; }
export interface CheckoutOptions { configurationVersion: number; methods: PaymentMethodOption[]; }
export interface ConfiguredMethod { code: string; enabled: boolean; recurring: boolean; monthlyOnly: boolean; minimumAmountJpy: number; maximumAmountJpy: number; displayOrder: number; citExecutionMode: PaymentExecutionMode; }
export interface ActiveConfiguration { version: number; publishedAt: string; publishedBy: string; methods: ConfiguredMethod[]; }
export interface ConfigurationWorkspace { active: ActiveConfiguration; draft: ActiveConfiguration | null; }
export interface CheckoutOptionsQuery { channel: DistributionChannel; amountJpy: number; monthly: boolean; ekycVerified: boolean; language: CheckoutLanguage; }
export interface BrowserPaymentConfiguration {
  liveCallsEnabled: boolean;
  webhooksEnabled: boolean;
  mpTokenJsUrl: string;
  shopId: string;
  publicBaseUrl: string;
  openapiWebhookUrl: string | null;
  protocolNotificationUrl: string | null;
}
export interface PaymentNextAction { type: 'NONE' | 'REDIRECT' | 'FORM_POST'; url: string | null; fields: Record<string, string>; }
export interface PaymentSubmission {
  transactionId: string;
  applicationNumber: string;
  method: string;
  state: string;
  providerStatus: string;
  requiresAttention: boolean;
  nextAction: PaymentNextAction;
  instructions: Record<string, unknown>;
  idempotentReplay: boolean;
}

/** Typed boundary for the public backend contract. Components never construct API URLs. */
@Injectable({ providedIn: 'root' })
export class CheckoutApiService {
  private readonly http = inject(HttpClient);

  getOptions(query: CheckoutOptionsQuery): Observable<CheckoutOptions> {
    const params = new HttpParams()
      .set('channel', query.channel).set('amountJpy', query.amountJpy)
      .set('monthly', query.monthly).set('ekycVerified', query.ekycVerified)
      .set('language', query.language);
    return this.http.get<CheckoutOptions>('/api/v1/checkout/options', { params });
  }

  getActiveConfiguration(): Observable<ActiveConfiguration> {
    return this.http.get<ActiveConfiguration>('/api/v1/configuration/active');
  }
  getConfigurationWorkspace(): Observable<ConfigurationWorkspace> {
    return this.http.get<ConfigurationWorkspace>('/api/v1/configuration/workspace');
  }
  saveConfigurationDraft(methods: ConfiguredMethod[], operatorToken: string): Observable<ActiveConfiguration> {
    return this.http.put<ActiveConfiguration>('/api/v1/configuration/draft', { methods },
      { headers: { 'X-Operator-Token': operatorToken } });
  }
  publishConfiguration(operatorToken: string): Observable<ActiveConfiguration> {
    return this.http.post<ActiveConfiguration>('/api/v1/configuration/draft/publish', {},
      { headers: { 'X-Operator-Token': operatorToken, 'X-Operator-Id': 'configuration-administrator' } });
  }
  discardConfigurationDraft(operatorToken: string): Observable<void> {
    return this.http.delete<void>('/api/v1/configuration/draft', { headers: { 'X-Operator-Token': operatorToken } });
  }

  getBrowserConfiguration(): Observable<BrowserPaymentConfiguration> {
    return this.http.get<BrowserPaymentConfiguration>('/api/v1/checkout/browser-configuration');
  }

  submitPayment(applicationNumber: string, method: string, details: Record<string, unknown>,
                idempotencyKey: string): Observable<PaymentSubmission> {
    return this.http.post<PaymentSubmission>(
      `/api/v1/checkout/applications/${encodeURIComponent(applicationNumber)}/payments`,
      { method, details }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }

  getPayment(transactionId: string): Observable<PaymentSubmission> {
    return this.http.get<PaymentSubmission>(
      `/api/v1/checkout/payments/${encodeURIComponent(transactionId)}`);
  }
}

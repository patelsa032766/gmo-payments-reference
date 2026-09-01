import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type CheckoutLanguage = 'en' | 'ja';
export type DistributionChannel = 'PA' | 'IA' | 'FI';
export interface PaymentMethodOption { code: string; label: string; description: string; recurring: boolean; displayOrder: number; }
export interface CheckoutOptions { configurationVersion: number; methods: PaymentMethodOption[]; }
export interface ConfiguredMethod { code: string; enabled: boolean; recurring: boolean; monthlyOnly: boolean; minimumAmountJpy: number; maximumAmountJpy: number; displayOrder: number; }
export interface ActiveConfiguration { version: number; publishedAt: string; publishedBy: string; methods: ConfiguredMethod[]; }
export interface CheckoutOptionsQuery { channel: DistributionChannel; amountJpy: number; monthly: boolean; ekycVerified: boolean; language: CheckoutLanguage; }

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
}

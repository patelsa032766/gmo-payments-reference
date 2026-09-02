import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaymentSubmission } from './checkout-api.service';

export interface TransactionSummary {
  transactionId: string; rootTransactionId: string | null; applicationNumber: string | null;
  amountJpy: number; canonicalState: string; method: string; productCode: string;
  initiationType: string; operation: string; customerName: string; customerCode: string;
  merchantReference: string; updatedAt: string; requiresAttention: boolean;
}
export interface TimelineEvent {
  eventId: string; eventType: string; source: string; summary: string;
  canonicalStateAfter: string; actor: string; correlationId: string;
  evidence: Record<string, unknown>; providerOccurredAt: string | null; occurredAt: string;
}
export interface ProviderExchange {
  exchangeId: string; eventId: string | null; direction: string; transport: string;
  operation: string; endpoint: string | null; httpStatus: number | null; durationMs: number | null;
  requestHeaders: Record<string, unknown>; requestBody: Record<string, unknown>;
  responseHeaders: Record<string, unknown>; responseBody: Record<string, unknown>;
  outcome: string; attemptNumber: number; correlationId: string; createdAt: string;
}
export interface TransactionThread { transaction: TransactionSummary; events: TimelineEvent[]; exchanges: ProviderExchange[]; }
export interface PaymentInstrument {
  instrumentId: string; customerCode: string; customerName: string; method: string;
  productCode: string; maskedDisplay: string; state: string; preferenceRole: string | null;
  metadata: Record<string, unknown>; updatedAt: string;
}
export interface KozaBatchSubmission { batchId:string;batchReference:string;state:string;submittedCount:number;totalJpy:number;payments:PaymentSubmission[]; }

@Injectable({ providedIn: 'root' })
export class OperationsApiService {
  private readonly http = inject(HttpClient);
  transactions(): Observable<TransactionSummary[]> { return this.http.get<TransactionSummary[]>('/api/v1/operations/transactions'); }
  thread(id: string): Observable<TransactionThread> { return this.http.get<TransactionThread>(`/api/v1/operations/transactions/${encodeURIComponent(id)}`); }
  instruments(): Observable<PaymentInstrument[]> { return this.http.get<PaymentInstrument[]>('/api/v1/mit/instruments'); }
  submitMit(instrumentId:string,amountJpy:number,merchantReference:string,authorizationMode:string,
            operatorToken:string,idempotencyKey:string):Observable<PaymentSubmission>{
    return this.http.post<PaymentSubmission>('/api/v1/mit/payments',
      {instrumentId,amountJpy,merchantReference,details:{authorizationMode}},
      {headers:{'X-Operator-Token':operatorToken,'Idempotency-Key':idempotencyKey}});
  }
  setPreferences(customerCode:string,primaryInstrumentId:string,backupInstrumentId:string|null,
                 operatorToken:string):Observable<PaymentInstrument[]>{
    return this.http.put<PaymentInstrument[]>(`/api/v1/mit/customers/${encodeURIComponent(customerCode)}/preferences`,
      {primaryInstrumentId,backupInstrumentId},{headers:{'X-Operator-Token':operatorToken}});
  }
  submitKozaBatch(request:Record<string,unknown>,operatorToken:string):Observable<KozaBatchSubmission>{
    return this.http.post<KozaBatchSubmission>('/api/v1/mit/koza-batches',request,
      {headers:{'X-Operator-Token':operatorToken,'X-Operator-Id':'payment-operator'}});
  }
}

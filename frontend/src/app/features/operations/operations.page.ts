import { DatePipe, JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckoutApiService } from '../../core/api/checkout-api.service';
import { OperationsApiService, ProviderExchange, TimelineEvent, TransactionSummary, TransactionThread } from '../../core/api/operations-api.service';

/** Sanitized payment ledger and evidence explorer backed by SQLite. */
@Component({ changeDetection: ChangeDetectionStrategy.OnPush, imports: [DatePipe, JsonPipe, FormsModule], selector: 'app-operations-page', styleUrl: './operations.page.scss', templateUrl: './operations.page.html' })
export class OperationsPage implements OnInit {
  private readonly api = inject(OperationsApiService);
  private readonly checkoutApi = inject(CheckoutApiService);
  protected readonly section = signal<'transactions'|'webhooks'|'sftp'>('transactions');
  protected readonly rows = signal<TransactionSummary[]>([]);
  protected readonly thread = signal<TransactionThread | null>(null);
  protected readonly selectedEvent = signal<TimelineEvent | null>(null);
  protected readonly selectedExchange = signal<ProviderExchange | null>(null);
  protected readonly loading = signal(true);
  protected readonly capturing = signal(false);
  protected readonly actionMessage = signal<string|null>(null);
  protected readonly failed = signal(false);
  protected readonly operatorTokenRequired = signal(true);
  protected readonly search = signal(''); protected readonly status = signal(''); protected readonly method = signal(''); protected timezone = '+0900';
  protected operatorToken='';
  protected timezoneLabel(): string { return this.timezone === '+0900' ? 'Tokyo' : this.timezone === '+0000' ? 'UTC' : 'Los Angeles'; }
  protected methodLabel(method: string): string {
    return ({
      CARD: 'Card', PAYPAY: 'PayPay', BANK_DIRECT_REALTIME: 'Real-time bank debit',
      KOZA_FURIKAE_SELECT: 'Koza Furikae', KOMBINI: 'Convenience store',
      PAYEASY: 'Pay-easy', FURIKOMI: 'Bank transfer',
    } as Record<string, string>)[method] ?? method;
  }
  protected readonly filtered = computed(() => this.rows().filter(row => {
    const query = this.search().trim().toLowerCase();
    return (!query || [row.transactionId,row.applicationNumber,row.customerName,row.customerCode,row.merchantReference]
      .some(value => value?.toLowerCase().includes(query)))
      && (!this.status() || row.canonicalState === this.status()) && (!this.method() || row.method === this.method());
  }));

  ngOnInit(): void {
    this.checkoutApi.getCheckoutExperience().subscribe(settings =>
      this.operatorTokenRequired.set(settings.operatorTokenRequired));
    this.reload();
  }
  protected switchSection(value: 'transactions'|'webhooks'|'sftp'): void { this.section.set(value); }
  protected selectTransaction(row: TransactionSummary): void {
    this.api.thread(row.transactionId).subscribe(thread => {
      this.thread.set(thread);
      const latestEvent = thread.events.at(-1) ?? null;
      this.selectedEvent.set(latestEvent);
      this.selectDefaultExchange(latestEvent);
    });
  }
  protected selectEvent(event: TimelineEvent): void {
    this.selectedEvent.set(event);
    this.selectDefaultExchange(event);
  }
  protected eventExchanges(): ProviderExchange[] {
    const eventId = this.selectedEvent()?.eventId;
    return eventId ? this.thread()?.exchanges.filter(exchange => exchange.eventId === eventId) ?? [] : [];
  }
  protected selectExchange(exchange: ProviderExchange): void { this.selectedExchange.set(exchange); }
  protected exchange(): ProviderExchange | null {
    return this.selectedExchange();
  }
  protected exchangeAmountJpy(): number | null {
    for (const exchange of this.eventExchanges()) {
      const rawAmount = exchange.requestBody['Amount'] ?? exchange.requestBody['amount'];
      if (rawAmount !== undefined && rawAmount !== null && String(rawAmount).trim() !== '') {
        const amount = Number(rawAmount);
        if (Number.isFinite(amount)) return amount;
      }
    }
    return null;
  }
  protected canCapture(thread:TransactionThread|null=this.thread()):boolean{
    return !!thread && thread.transaction.canonicalState==='AUTHORIZED'
      && (thread.transaction.method==='CARD'||thread.transaction.method==='PAYPAY')
      && !this.isSimulatedAuthorization(thread);
  }
  /** Historical prototype authorizations have no GMO order to capture. */
  protected isSimulatedAuthorization(thread:TransactionThread|null=this.thread()):boolean{
    return !!thread && thread.exchanges.some(exchange =>
      exchange.operation.startsWith('Simulated')
      || String(exchange.requestBody['mode'] ?? '').toUpperCase()==='SIMULATED');
  }
  protected capture():void{
    const selected=this.thread();
    if(!this.canCapture(selected))return;
    if(this.operatorTokenRequired()&&!this.operatorToken){this.actionMessage.set('Enter the operator token to capture this authorization.');return;}
    const transaction=selected!.transaction;
    if(!window.confirm(`Capture JPY ${transaction.amountJpy.toLocaleString()} for ${transaction.transactionId}? This sends a financial request to GMO.`))return;
    this.capturing.set(true);this.actionMessage.set(null);
    this.api.capture(transaction.transactionId,this.operatorToken,crypto.randomUUID()).subscribe({
      next:result=>{this.capturing.set(false);this.actionMessage.set(result.state==='PAID'
        ? 'PAID · Capture recorded in this transaction thread.'
        : `${result.state} · Capture was not completed; review the newest event before another action.`);this.refreshSelected(transaction.transactionId);},
      error:()=>{this.capturing.set(false);this.actionMessage.set('Capture was not completed. Review the latest transaction event before trying again.');this.refreshSelected(transaction.transactionId);}
    });
  }
  private refreshSelected(transactionId:string):void{
    this.api.transactions().subscribe(rows=>{this.rows.set(rows);const row=rows.find(item=>item.transactionId===transactionId);if(row)this.selectTransaction(row);});
  }
  /**
   * A lifecycle event may contain several provider calls. Select the final call
   * as the outcome by default while retaining the ordered call sequence so an
   * operator can inspect registration inquiry, amount-bearing entry, and
   * execution independently.
   */
  private selectDefaultExchange(event: TimelineEvent | null): void {
    const eventId = event?.eventId;
    const exchanges = eventId
      ? this.thread()?.exchanges.filter(exchange => exchange.eventId === eventId) ?? []
      : [];
    this.selectedExchange.set(exchanges.at(-1) ?? null);
  }
  protected reload(): void {
    this.loading.set(true); this.failed.set(false);
    this.api.transactions().subscribe({ next: rows => { this.rows.set(rows); this.loading.set(false); if (rows[0]) this.selectTransaction(rows[0]); },
      error: () => { this.failed.set(true); this.loading.set(false); } });
  }
}

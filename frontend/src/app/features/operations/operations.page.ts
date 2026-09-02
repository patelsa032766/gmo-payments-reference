import { DatePipe, JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OperationsApiService, ProviderExchange, TimelineEvent, TransactionSummary, TransactionThread } from '../../core/api/operations-api.service';

/** Sanitized payment ledger and evidence explorer backed by SQLite. */
@Component({ changeDetection: ChangeDetectionStrategy.OnPush, imports: [DatePipe, JsonPipe, FormsModule], selector: 'app-operations-page', styleUrl: './operations.page.scss', templateUrl: './operations.page.html' })
export class OperationsPage implements OnInit {
  private readonly api = inject(OperationsApiService);
  protected readonly section = signal<'transactions'|'webhooks'|'sftp'>('transactions');
  protected readonly rows = signal<TransactionSummary[]>([]);
  protected readonly thread = signal<TransactionThread | null>(null);
  protected readonly selectedEvent = signal<TimelineEvent | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly search = signal(''); protected readonly status = signal(''); protected readonly method = signal(''); protected timezone = '+0900';
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

  ngOnInit(): void { this.reload(); }
  protected switchSection(value: 'transactions'|'webhooks'|'sftp'): void { this.section.set(value); }
  protected selectTransaction(row: TransactionSummary): void {
    this.api.thread(row.transactionId).subscribe(thread => { this.thread.set(thread); this.selectedEvent.set(thread.events.at(-1) ?? null); });
  }
  protected selectEvent(event: TimelineEvent): void { this.selectedEvent.set(event); }
  protected exchange(): ProviderExchange | null {
    const event = this.selectedEvent(); return this.thread()?.exchanges.find(exchange => exchange.eventId === event?.eventId) ?? null;
  }
  protected reload(): void {
    this.loading.set(true); this.failed.set(false);
    this.api.transactions().subscribe({ next: rows => { this.rows.set(rows); this.loading.set(false); if (rows[0]) this.selectTransaction(rows[0]); },
      error: () => { this.failed.set(true); this.loading.set(false); } });
  }
}

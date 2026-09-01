import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActiveConfiguration, CheckoutApiService } from '../../core/api/checkout-api.service';

/** Operator view of the published payment-method release. Draft editing is the next slice. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-configuration-page',
  styleUrl: './configuration.page.scss',
  templateUrl: './configuration.page.html',
})
export class ConfigurationPage implements OnInit {
  private readonly api = inject(CheckoutApiService);
  protected readonly configuration = signal<ActiveConfiguration | null>(null);
  protected readonly failed = signal(false);

  ngOnInit(): void {
    this.api.getActiveConfiguration().subscribe({
      next: (configuration) => this.configuration.set(configuration),
      error: () => this.failed.set(true),
    });
  }
}

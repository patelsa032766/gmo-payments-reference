import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Operations landing page; event persistence and webhook ingestion arrive in a later slice. */
@Component({ changeDetection: ChangeDetectionStrategy.OnPush, selector: 'app-operations-page', styleUrl: './operations.page.scss', templateUrl: './operations.page.html' })
export class OperationsPage {}

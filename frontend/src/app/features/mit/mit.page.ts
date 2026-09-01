import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Operator MIT form shell. Submission remains disabled until the transaction command API lands. */
@Component({ changeDetection: ChangeDetectionStrategy.OnPush, selector: 'app-mit-page', styleUrl: './mit.page.scss', templateUrl: './mit.page.html' })
export class MitPage {}

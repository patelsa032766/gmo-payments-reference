import { Injectable } from '@angular/core';

/**
 * Holds the operator credential only in the running Angular application.
 *
 * Route changes reuse this root-scoped service, while a reload or closed tab
 * clears the value. The token is intentionally not written to localStorage,
 * sessionStorage, source files, logs, or backend configuration responses.
 */
@Injectable({ providedIn: 'root' })
export class OperatorCredentialService {
  private token = '';

  current(): string {
    return this.token;
  }

  rememberForCurrentTab(token: string): void {
    this.token = token;
  }
}

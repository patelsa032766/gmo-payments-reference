import { Routes } from '@angular/router';

/** Route-level lazy loading keeps the customer checkout isolated from operator code. */
export const routes: Routes = [
  { path: 'checkout', loadComponent: () => import('./features/checkout/checkout.page').then((m) => m.CheckoutPage) },
  { path: 'configuration', loadComponent: () => import('./features/configuration/configuration.page').then((m) => m.ConfigurationPage) },
  { path: 'operations', loadComponent: () => import('./features/operations/operations.page').then((m) => m.OperationsPage) },
  { path: 'mit', loadComponent: () => import('./features/mit/mit.page').then((m) => m.MitPage) },
  { path: '', pathMatch: 'full', redirectTo: 'checkout' },
  { path: '**', redirectTo: 'checkout' },
];

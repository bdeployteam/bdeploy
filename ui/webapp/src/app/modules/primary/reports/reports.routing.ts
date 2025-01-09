import { Route } from '@angular/router';



export const REPORTS_ROUTES: Route[] = [
  {
    path: 'browser',
    loadComponent: () => import('./components/reports-browser/reports-browser.component').then(m => m.ReportsBrowserComponent),
  },
  {
    path: 'browser/:report/view',
    loadComponent: () => import('./components/report/report.component').then(m => m.ReportComponent),
  },
];

import { Route } from '@angular/router';




export const REPORTS_PANEL_ROUTES: Route[] = [
  {
    path: ':report/form',
    loadComponent: () => import('./components/form/report-form.component').then(m => m.ReportFormComponent),
  },
  {
    path: 'details',
    loadComponent: () => import('./components/details/report-details.component').then(m => m.ReportDetailsComponent),
  },
  {
    path: 'row-details',
    loadComponent: () => import('./components/row-details/report-row-details.component').then(m => m.ReportRowDetailsComponent),
  },
];

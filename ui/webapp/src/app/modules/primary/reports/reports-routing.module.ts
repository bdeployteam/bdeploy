import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ReportComponent } from './components/report/report.component';
import { ReportsBrowserComponent } from './components/reports-browser/reports-browser.component';

const REPORTS_ROUTES: Route[] = [
  {
    path: 'browser',
    component: ReportsBrowserComponent,
  },
  {
    path: 'browser/:report/view',
    component: ReportComponent,
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(REPORTS_ROUTES))],
  exports: [RouterModule],
})
export class ReportsRoutingModule {}

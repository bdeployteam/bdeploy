import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ReportsBrowserComponent } from './components/reports-browser/reports-browser.component';

const REPORTS_ROUTES: Route[] = [
  {
    path: 'browser',
    component: ReportsBrowserComponent,
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(REPORTS_ROUTES))],
  exports: [RouterModule],
})
export class ReportsRoutingModule {}

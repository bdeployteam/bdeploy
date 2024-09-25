import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ReportFormComponent } from './components/form/report-form.component';
import { ReportComponent } from './components/report/report.component';

const REPORTS_ROUTES: Route[] = [
  {
    path: ':report/form',
    component: ReportFormComponent,
  },
  {
    path: ':report/view',
    component: ReportComponent,
    data: { max: true },
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(REPORTS_ROUTES))],
  exports: [RouterModule],
})
export class ReportsRoutingModule {}

import { NgModule } from '@angular/core';
import { Route, RouterModule } from '@angular/router';
import { setRouteId } from '../../core/utils/routeId-generator';
import { ReportDetailsComponent } from './components/details/report-details.component';
import { ReportFormComponent } from './components/form/report-form.component';
import { ReportRowDetailsComponent } from './components/row-details/report-row-details.component';

const REPORTS_ROUTES: Route[] = [
  {
    path: ':report/form',
    component: ReportFormComponent,
  },
  {
    path: 'details',
    component: ReportDetailsComponent,
  },
  {
    path: 'row-details',
    component: ReportRowDetailsComponent,
  },
];

@NgModule({
  imports: [RouterModule.forChild(setRouteId(REPORTS_ROUTES))],
  exports: [RouterModule],
})
export class ReportsRoutingModule {}

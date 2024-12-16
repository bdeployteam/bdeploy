import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ReportDetailsComponent } from './components/details/report-details.component';
import { ReportFormInputComponent } from './components/form-input/report-form-input.component';
import { ReportFormComponent } from './components/form/report-form.component';
import { ReportRowDetailsComponent } from './components/row-details/report-row-details.component';
import { ReportsRoutingModule } from './reports-routing.module';

@NgModule({
  declarations: [ReportFormComponent, ReportFormInputComponent, ReportDetailsComponent, ReportRowDetailsComponent],
  imports: [CommonModule, CoreModule, ReportsRoutingModule],
})
export class ReportsModule {}

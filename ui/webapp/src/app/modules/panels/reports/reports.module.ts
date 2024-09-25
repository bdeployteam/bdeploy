import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ReportFormInputComponent } from './components/form-input/report-form-input.component';
import { ReportFormComponent } from './components/form/report-form.component';
import { ReportComponent } from './components/report/report.component';
import { ReportsRoutingModule } from './reports-routing.module';

@NgModule({
  declarations: [ReportComponent, ReportFormComponent, ReportFormInputComponent],
  imports: [CommonModule, CoreModule, ReportsRoutingModule],
})
export class ReportsModule {}

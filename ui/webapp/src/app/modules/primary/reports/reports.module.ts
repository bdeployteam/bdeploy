import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { CoreModule } from '../../core/core.module';
import { ReportsBrowserComponent } from './components/reports-browser/reports-browser.component';
import { ReportsRoutingModule } from './reports-routing.module';

@NgModule({
  declarations: [ReportsBrowserComponent],
  imports: [CommonModule, CoreModule, ReportsRoutingModule],
})
export class ReportsModule {}
